# scripts/profile/video_readiness.py
#
# ForenShield AI — 영상 분석 적합성(Analysis Readiness) 프로파일러.
#
# AI 딥페이크·위변조 분석을 시작하기 전, 영상 화질이 분석에 충분한지
# 사전 검사하는 모듈입니다. 위변조 여부를 판별하지 않으며, blur·압축 손실·
# FFT 격자 아티팩트 등으로 「분석 신뢰도가 낮아질 수 있음」을 안내합니다.
#
# 출처:
#   docs/notebooks/test/실시간_블록_노이즈_히트맵.ipynb  (3×3 worst-region)
#   docs/notebooks/test/실시간_블록_격자없음.ipynb        (sample_every 샘플링)
#
# 주요 함수:
#   analyze_video_readiness()  — 영상 경로 입력 → 등급(GOOD/CAUTION/POOR/BLOCK) + JSON
#   calculate_blur_score()     — Laplacian 선명도
#   calculate_blockiness_heatmap() — 8×8 블록 경계 압축 손실
#   calculate_fft_grid_peak()  — FFT 고주파 격자 peak
#
# CLI:
#   python scripts/profile/video_readiness.py <video.mp4> --pretty


# 프레임 샘플링: n프레임마다 한 장씩 뽑아 분석(기본 10프레임 간격)
# blur: Laplacian으로 선명도 측정 (낮으면 흐림)
# blockiness: 8x8 압축 블록 경계 손실(높으면 sns 재압축등)
# FFT peak: 고주파 격자 아티팩트(높으면 격자 노이즈)

# 등급 판정 GOOD/CAUTION/POOR/BLOCK
#JSON 출력: 백엔드 API에서 쓸 수 있는 to_dict() 메서드 

"""
Video analysis readiness profiler.

Ports the notebook prototypes in docs/notebooks/test/:
  - 실시간_블록_노이즈_히트맵.ipynb (3x3 worst-region blockiness)
  - 실시간_블록_격자없음.ipynb (sample_every slider)

Computes blur (Laplacian), blockiness (8x8 boundary energy), and FFT grid peak
on sampled frames. Output is suitable for backend readiness-check APIs.
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Iterator, Literal

import cv2
import numpy as np

ReadinessTier = Literal["BLOCK", "POOR", "CAUTION", "GOOD"]


@dataclass(frozen=True)
class ReadinessThresholds:
    """Notebook UI prototype thresholds + metadata gates for readiness tiers."""

    # Per-frame UI thresholds (notebook overlays)
    blur_low_lt: float = 100.0
    blockiness_high_gt: float = 30.0
    fft_peak_high_gt: float = 0.4
    worst_region_alert_gt: float = 20.0

    # Metadata gates
    min_duration_sec: float = 3.0
    min_fps: float = 15.0
    caution_min_pixels: int = 640 * 480
    poor_min_pixels: int = 426 * 240

    # Aggregate gates for tiering (blur mean across sampled frames)
    poor_blur_min_lt: float = 80.0
    caution_blur_min_lt: float = 100.0

    confidence_cap_poor: int = 60
    confidence_cap_caution: int = 75


@dataclass
class MetricAggregate:
    mean: float
    min: float | None = None
    max: float | None = None

    def to_dict(self) -> dict[str, float | None]:
        return asdict(self)


@dataclass
class FrameSample:
    frame_index: int
    blur: float
    blockiness: float
    fft_peak: float
    worst_region: str
    worst_region_score: float
    flags: dict[str, bool] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class VideoReadinessResult:
    video_path: str
    status: Literal["ok", "error"]
    error: str | None = None

    total_frames: int = 0
    sampled_frames: int = 0
    sample_every: int = 10
    width: int | None = None
    height: int | None = None
    fps: float | None = None
    duration_sec: float | None = None

    blur: MetricAggregate | None = None
    blockiness: MetricAggregate | None = None
    fft_peak: MetricAggregate | None = None

    worst_region: str = ""
    worst_region_score: float = 0.0
    is_spatially_uniform: bool = True

    readiness_tier: ReadinessTier = "GOOD"
    confidence_cap: int = 100
    reasons: list[str] = field(default_factory=list)
    requires_acknowledgement: bool = False

    frame_samples: list[FrameSample] = field(default_factory=list)
    thresholds_version: str = "notebook-ui-v1"

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "videoPath": self.video_path,
            "status": self.status,
            "error": self.error,
            "videoMetadata": {
                "totalFrames": self.total_frames,
                "sampledFrames": self.sampled_frames,
                "sampleEvery": self.sample_every,
                "width": self.width,
                "height": self.height,
                "fps": self.fps,
                "durationSec": self.duration_sec,
            },
            "frameMetrics": None,
            "spatial": {
                "worstRegion": self.worst_region,
                "worstRegionScore": round(self.worst_region_score, 2),
                "isSpatiallyUniform": self.is_spatially_uniform,
            },
            "readinessTier": self.readiness_tier,
            "confidenceCap": self.confidence_cap,
            "reasons": self.reasons,
            "requiresAcknowledgement": self.requires_acknowledgement,
            "thresholdsVersion": self.thresholds_version,
            "frameSamples": [sample.to_dict() for sample in self.frame_samples],
        }

        if self.blur and self.blockiness and self.fft_peak:
            payload["frameMetrics"] = {
                "blur": self.blur.to_dict(),
                "blockiness": self.blockiness.to_dict(),
                "fftPeak": self.fft_peak.to_dict(),
            }

        return payload


def calculate_blockiness_heatmap(
    gray_frame: np.ndarray,
    *,
    return_heatmap: bool = True,
) -> tuple[np.ndarray | None, float, str, float]:
    """
    8x8 block-boundary energy heatmap with global and 3x3 regional scores.

    Returns (color_heatmap_bgr|None, global_score, worst_region_name, worst_region_score).
    Set return_heatmap=False in production readiness checks to avoid large colormap allocations.
    """
    h, w = gray_frame.shape
    mask = np.zeros((h, w), dtype=np.float32)

    gray_float = gray_frame.astype(np.float32)
    h_bnd = np.arange(7, h - 1, 8)
    w_bnd = np.arange(7, w - 1, 8)

    diff_h = np.abs(gray_float[h_bnd, :] - gray_float[h_bnd + 1, :])
    diff_v = np.abs(gray_float[:, w_bnd] - gray_float[:, w_bnd + 1])

    mask[h_bnd, :] += diff_h
    mask[h_bnd + 1, :] += diff_h
    mask[:, w_bnd] += diff_v
    mask[:, w_bnd + 1] += diff_v

    raw_score = (float(np.mean(diff_h)) + float(np.mean(diff_v))) / 2.0
    current_score = min(raw_score * 10.0, 100.0)

    grid_h, grid_w = h // 3, w // 3
    max_grid_score = 0.0
    max_grid_loc = ""

    locations = (
        ("Top-Left", 0, grid_h, 0, grid_w),
        ("Top-Center", 0, grid_h, grid_w, grid_w * 2),
        ("Top-Right", 0, grid_h, grid_w * 2, w),
        ("Mid-Left", grid_h, grid_h * 2, 0, grid_w),
        ("Center", grid_h, grid_h * 2, grid_w, grid_w * 2),
        ("Mid-Right", grid_h, grid_h * 2, grid_w * 2, w),
        ("Bot-Left", grid_h * 2, h, 0, grid_w),
        ("Bot-Center", grid_h * 2, h, grid_w, grid_w * 2),
        ("Bot-Right", grid_h * 2, h, grid_w * 2, w),
    )

    for name, y1, y2, x1, x2 in locations:
        grid_mask = mask[y1:y2, x1:x2]
        non_zero_vals = grid_mask[grid_mask > 0]
        if len(non_zero_vals) > 0:
            grid_score = min(float(np.mean(non_zero_vals)) * 5.0, 100.0)
            if grid_score > max_grid_score:
                max_grid_score = grid_score
                max_grid_loc = name

    if not return_heatmap:
        return None, current_score, max_grid_loc, max_grid_score

    heatmap_blurred = cv2.GaussianBlur(mask, (15, 15), 0)
    heatmap_absolute = np.clip(heatmap_blurred * 10.0, 0, 255).astype(np.uint8)
    color_map = cv2.applyColorMap(heatmap_absolute, cv2.COLORMAP_JET)

    return color_map, current_score, max_grid_loc, max_grid_score


# Backend pods are ~1Gi. 8K/HEVC full-frame FFT OOMs; keep native pixel scale via
# center-crop (no resize) when the frame exceeds the analysis window.
_DEFAULT_MAX_METRIC_FRAMES = 48
_METRIC_MAX_WIDTH = 1920
_METRIC_MAX_HEIGHT = 1080


def _adaptive_sample_every(sample_every: int, width: int | None, height: int | None) -> int:
    """Sparse sampling for high-res / HEVC-heavy clips to keep decoder pressure down."""
    every = max(1, sample_every)
    pixels = (width or 0) * (height or 0)
    if pixels >= 3840 * 2160:
        every = max(every, 60)
    elif pixels >= 1920 * 1080:
        every = max(every, 30)
    elif pixels >= 1280 * 720:
        every = max(every, 20)
    return every


def _adaptive_max_metric_frames(max_metric_frames: int, width: int | None, height: int | None) -> int:
    limit = max(1, max_metric_frames)
    pixels = (width or 0) * (height or 0)
    if pixels >= 3840 * 2160:
        return min(limit, 12)
    if pixels >= 1920 * 1080:
        return min(limit, 24)
    return limit


def _prepare_gray_for_metrics(
    gray_frame: np.ndarray,
    *,
    max_width: int = _METRIC_MAX_WIDTH,
    max_height: int = _METRIC_MAX_HEIGHT,
) -> np.ndarray:
    """
    Keep native pixel scale (no resize) so blur/blockiness thresholds stay comparable.
    Oversized frames (4K/8K) use a center crop within max_width x max_height.
    """
    height, width = gray_frame.shape[:2]
    if width <= max_width and height <= max_height:
        return gray_frame
    crop_w = min(width, max_width)
    crop_h = min(height, max_height)
    x0 = (width - crop_w) // 2
    y0 = (height - crop_h) // 2
    return gray_frame[y0 : y0 + crop_h, x0 : x0 + crop_w]


def _ffmpeg_available() -> bool:
    return shutil.which("ffmpeg") is not None


def _score_gray_frame(
    gray: np.ndarray,
    thresholds: ReadinessThresholds,
) -> tuple[float, float, float, str, float]:
    _, blockiness_score, region_name, region_score = calculate_blockiness_heatmap(
        gray,
        return_heatmap=False,
    )
    blur_score = calculate_blur_score(gray)
    fft_score = calculate_fft_grid_peak(gray)
    return blur_score, blockiness_score, fft_score, region_name, region_score


def _iter_metric_grays_via_ffmpeg(
    video_path: Path,
    *,
    sample_every: int,
    metric_limit: int,
    src_width: int,
    src_height: int,
) -> Iterator[np.ndarray]:
    """
    Decode only cropped gray frames via ffmpeg so Python never materializes full 8K BGR.
    Crop keeps native pixel scale (no scale filter).
    """
    crop_w = min(_METRIC_MAX_WIDTH, src_width)
    crop_h = min(_METRIC_MAX_HEIGHT, src_height)
    # n is 0-based in ffmpeg select.
    vf = (
        f"select=not(mod(n\\,{max(1, sample_every)})),"
        f"crop={crop_w}:{crop_h},"
        f"format=gray"
    )
    command = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        str(video_path),
        "-vf",
        vf,
        "-vsync",
        "vfr",
        "-frames:v",
        str(max(1, metric_limit)),
        "-f",
        "rawvideo",
        "-pix_fmt",
        "gray",
        "pipe:1",
    ]
    frame_bytes = crop_w * crop_h
    process = subprocess.Popen(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    assert process.stdout is not None
    try:
        produced = 0
        while produced < metric_limit:
            raw = process.stdout.read(frame_bytes)
            if not raw or len(raw) < frame_bytes:
                break
            gray = np.frombuffer(raw, dtype=np.uint8).reshape((crop_h, crop_w))
            produced += 1
            yield gray
    finally:
        process.stdout.close()
        if process.poll() is None:
            process.kill()
        process.wait(timeout=30)


def calculate_blur_score(gray_frame: np.ndarray) -> float:
    return float(cv2.Laplacian(gray_frame, cv2.CV_64F).var())


def calculate_fft_grid_peak(gray_frame: np.ndarray) -> float:
    f_transform = np.fft.fft2(gray_frame)
    f_shift = np.fft.fftshift(f_transform)
    magnitude_spectrum = 20 * np.log(np.abs(f_shift) + 1)

    h, w = magnitude_spectrum.shape
    cy, cx = h // 2, w // 2
    mask_radius = 30
    y_grid, x_grid = np.ogrid[:h, :w]
    dist_from_center = np.sqrt((x_grid - cx) ** 2 + (y_grid - cy) ** 2)
    high_freq_region = magnitude_spectrum[dist_from_center > mask_radius]

    if len(high_freq_region) == 0:
        return 0.0

    peak_strength = float(np.std(high_freq_region)) / 100.0
    return min(peak_strength, 1.0)


def _frame_flags(
    blur: float,
    blockiness: float,
    fft_peak: float,
    worst_region_score: float,
    thresholds: ReadinessThresholds,
) -> dict[str, bool]:
    return {
        "blurLow": blur < thresholds.blur_low_lt,
        "blockinessHigh": blockiness > thresholds.blockiness_high_gt,
        "fftPeakHigh": fft_peak > thresholds.fft_peak_high_gt,
        "worstRegionAlert": worst_region_score > thresholds.worst_region_alert_gt,
    }


def _evaluate_readiness_tier(
    *,
    width: int | None,
    height: int | None,
    fps: float | None,
    duration_sec: float | None,
    blur_mean: float | None,
    blockiness_max: float | None,
    fft_peak_max: float | None,
    thresholds: ReadinessThresholds,
) -> tuple[ReadinessTier, int, list[str], bool]:
    reasons: list[str] = []
    tier: ReadinessTier = "GOOD"

    pixels = (width or 0) * (height or 0)
    if width and height:
        if pixels < thresholds.poor_min_pixels:
            tier = "POOR"
            reasons.append(
                f"해상도 {width}x{height} (권장 720p 이상, 최소 480p)"
            )
        elif pixels < thresholds.caution_min_pixels:
            tier = _max_tier(tier, "CAUTION")
            reasons.append(
                f"해상도 {width}x{height} (권장 1280x720 이상)"
            )

    if duration_sec is not None and duration_sec < thresholds.min_duration_sec:
        tier = "POOR"
        reasons.append(
            f"재생 시간 {duration_sec:.1f}초 (권장 {thresholds.min_duration_sec:.0f}초 이상)"
        )

    if fps is not None and 0 < fps < thresholds.min_fps:
        tier = _max_tier(tier, "CAUTION")
        reasons.append(f"FPS {fps:.1f} (권장 {thresholds.min_fps:.0f} 이상)")

    if blur_mean is not None:
        if blur_mean < thresholds.poor_blur_min_lt:
            tier = "POOR"
            reasons.append(
                f"선명도(blur) 평균값 {blur_mean:.1f} (권장 {thresholds.blur_low_lt:.0f} 이상)"
            )
        elif blur_mean < thresholds.caution_blur_min_lt:
            tier = _max_tier(tier, "CAUTION")
            reasons.append(
                f"선명도(blur) 평균값 {blur_mean:.1f} (권장 {thresholds.blur_low_lt:.0f} 이상)"
            )

    if blockiness_max is not None and blockiness_max > thresholds.blockiness_high_gt:
        tier = _max_tier(tier, "CAUTION")
        reasons.append(
            f"압축 블록 손실 최고값 {blockiness_max:.1f} "
            f"(참고 임계값 {thresholds.blockiness_high_gt:.0f})"
        )

    if fft_peak_max is not None and fft_peak_max > thresholds.fft_peak_high_gt:
        tier = _max_tier(tier, "CAUTION")
        reasons.append(
            f"FFT 격자 peak 최고값 {fft_peak_max:.2f} "
            f"(참고 임계값 {thresholds.fft_peak_high_gt:.2f})"
        )

    confidence_cap = 100
    if tier == "POOR":
        confidence_cap = thresholds.confidence_cap_poor
    elif tier == "CAUTION":
        confidence_cap = thresholds.confidence_cap_caution

    requires_ack = tier in ("POOR", "CAUTION")
    return tier, confidence_cap, reasons, requires_ack


def _max_tier(current: ReadinessTier, candidate: ReadinessTier) -> ReadinessTier:
    order = {"GOOD": 0, "CAUTION": 1, "POOR": 2, "BLOCK": 3}
    return current if order[current] >= order[candidate] else candidate


def analyze_video_readiness(
    video_path: str | Path,
    *,
    sample_every: int = 10,
    max_frame_samples: int | None = 120,
    max_metric_frames: int = _DEFAULT_MAX_METRIC_FRAMES,
    thresholds: ReadinessThresholds | None = None,
    include_frame_samples: bool = True,
) -> VideoReadinessResult:
    """
    Sample frames from a video and compute readiness metrics.

    Args:
        video_path: Local path to a video file readable by OpenCV.
        sample_every: Process every Nth frame (notebook default: 10).
        max_frame_samples: Cap stored per-frame samples (None = keep all).
        max_metric_frames: Stop metric computation after this many sampled frames
            (keeps HEVC/OpenCV memory bounded on small backend pods).
        thresholds: Tiering thresholds; defaults to notebook UI values.
        include_frame_samples: When False, omit per-frame list from result.
    """
    thresholds = thresholds or ReadinessThresholds()
    path = Path(video_path)
    result = VideoReadinessResult(
        video_path=str(path),
        status="error",
        sample_every=sample_every,
    )

    cap = cv2.VideoCapture(str(path))
    if not cap.isOpened():
        result.error = f"Cannot open video: {path}"
        result.readiness_tier = "BLOCK"
        result.reasons = ["영상 파일을 열 수 없습니다."]
        result.requires_acknowledgement = False
        return result

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)) or None
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)) or None
    fps = float(cap.get(cv2.CAP_PROP_FPS)) or None
    if fps is not None and fps <= 0:
        fps = None

    reported_total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) or 0
    sample_every = _adaptive_sample_every(sample_every, width, height)
    result.sample_every = sample_every
    metric_limit = _adaptive_max_metric_frames(max_metric_frames, width, height)

    blur_values: list[float] = []
    blockiness_values: list[float] = []
    fft_values: list[float] = []
    frame_samples: list[FrameSample] = []

    worst_region = ""
    worst_region_score = 0.0
    frame_idx = 0

    use_ffmpeg = (
        width is not None
        and height is not None
        and width * height >= 3840 * 2160
        and _ffmpeg_available()
    )

    if use_ffmpeg:
        # Drop OpenCV decoder before ffmpeg path — 8K HEVC decode buffers alone can OOM.
        cap.release()
        for gray in _iter_metric_grays_via_ffmpeg(
            path,
            sample_every=sample_every,
            metric_limit=metric_limit,
            src_width=width,
            src_height=height,
        ):
            frame_idx += sample_every
            (
                blur_score,
                blockiness_score,
                fft_score,
                region_name,
                region_score,
            ) = _score_gray_frame(gray, thresholds)
            del gray

            blur_values.append(blur_score)
            blockiness_values.append(blockiness_score)
            fft_values.append(fft_score)

            if region_score > worst_region_score:
                worst_region_score = region_score
                worst_region = region_name

            if include_frame_samples and (
                max_frame_samples is None or len(frame_samples) < max_frame_samples
            ):
                frame_samples.append(
                    FrameSample(
                        frame_index=frame_idx,
                        blur=round(blur_score, 2),
                        blockiness=round(blockiness_score, 2),
                        fft_peak=round(fft_score, 4),
                        worst_region=region_name,
                        worst_region_score=round(region_score, 2),
                        flags=_frame_flags(
                            blur_score,
                            blockiness_score,
                            fft_score,
                            region_score,
                            thresholds,
                        ),
                    )
                )
    else:
        while True:
            # grab() skips full decode on non-sampled frames (important for HEVC memory).
            if not cap.grab():
                break

            frame_idx += 1
            if frame_idx % sample_every != 0:
                continue

            ret, frame = cap.retrieve()
            if not ret or frame is None:
                continue

            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            del frame
            gray = _prepare_gray_for_metrics(gray)

            (
                blur_score,
                blockiness_score,
                fft_score,
                region_name,
                region_score,
            ) = _score_gray_frame(gray, thresholds)
            del gray

            blur_values.append(blur_score)
            blockiness_values.append(blockiness_score)
            fft_values.append(fft_score)

            if region_score > worst_region_score:
                worst_region_score = region_score
                worst_region = region_name

            if include_frame_samples and (
                max_frame_samples is None or len(frame_samples) < max_frame_samples
            ):
                frame_samples.append(
                    FrameSample(
                        frame_index=frame_idx,
                        blur=round(blur_score, 2),
                        blockiness=round(blockiness_score, 2),
                        fft_peak=round(fft_score, 4),
                        worst_region=region_name,
                        worst_region_score=round(region_score, 2),
                        flags=_frame_flags(
                            blur_score,
                            blockiness_score,
                            fft_score,
                            region_score,
                            thresholds,
                        ),
                    )
                )

            if len(blur_values) >= metric_limit:
                break

        cap.release()

    result.total_frames = reported_total if reported_total > 0 else frame_idx
    result.sampled_frames = len(blur_values)
    result.width = width
    result.height = height
    result.fps = fps
    if fps and result.total_frames > 0:
        result.duration_sec = result.total_frames / fps
    elif fps and frame_idx > 0:
        result.duration_sec = frame_idx / fps
    else:
        result.duration_sec = None
    result.worst_region = worst_region
    result.worst_region_score = worst_region_score
    result.is_spatially_uniform = worst_region_score <= thresholds.worst_region_alert_gt
    result.frame_samples = frame_samples

    if frame_idx == 0 or not blur_values:
        result.error = "No decodable frames"
        result.readiness_tier = "BLOCK"
        result.reasons = ["디코딩 가능한 프레임이 없습니다."]
        result.requires_acknowledgement = False
        return result

    result.status = "ok"
    blur_min = float(np.min(blur_values))
    blur_mean = float(np.mean(blur_values))
    blockiness_mean = float(np.mean(blockiness_values))
    blockiness_max = float(np.max(blockiness_values))
    fft_mean = float(np.mean(fft_values))
    fft_max = float(np.max(fft_values))

    result.blur = MetricAggregate(mean=round(blur_mean, 2), min=round(blur_min, 2))
    result.blockiness = MetricAggregate(
        mean=round(blockiness_mean, 2),
        max=round(blockiness_max, 2),
    )
    result.fft_peak = MetricAggregate(mean=round(fft_mean, 4), max=round(fft_max, 4))

    tier, confidence_cap, reasons, requires_ack = _evaluate_readiness_tier(
        width=width,
        height=height,
        fps=fps,
        duration_sec=result.duration_sec,
        blur_mean=blur_mean,
        blockiness_max=blockiness_max,
        fft_peak_max=fft_max,
        thresholds=thresholds,
    )
    result.readiness_tier = tier
    result.confidence_cap = confidence_cap
    result.reasons = reasons
    result.requires_acknowledgement = requires_ack
    return result


def _build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Analyze video readiness (blur / blockiness / FFT peak)."
    )
    parser.add_argument("video", type=Path, help="Path to input video")
    parser.add_argument(
        "--sample-every",
        type=int,
        default=10,
        help="Sample every N frames (default: 10)",
    )
    parser.add_argument(
        "--max-samples",
        type=int,
        default=120,
        help="Max per-frame samples in JSON output (default: 120)",
    )
    parser.add_argument(
        "--max-metric-frames",
        type=int,
        default=_DEFAULT_MAX_METRIC_FRAMES,
        help=f"Max frames to score before stopping (default: {_DEFAULT_MAX_METRIC_FRAMES})",
    )
    parser.add_argument(
        "--no-frame-samples",
        action="store_true",
        help="Omit per-frame sample list from JSON output",
    )
    parser.add_argument(
        "--pretty",
        action="store_true",
        help="Pretty-print JSON",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _build_arg_parser().parse_args(argv)

    result = analyze_video_readiness(
        args.video,
        sample_every=max(1, args.sample_every),
        max_frame_samples=args.max_samples,
        max_metric_frames=max(1, args.max_metric_frames),
        include_frame_samples=not args.no_frame_samples,
    )

    indent = 2 if args.pretty else None
    print(json.dumps(result.to_dict(), ensure_ascii=False, indent=indent))

    if result.status != "ok":
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
