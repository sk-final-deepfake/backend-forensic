package com.example.demo.service;

import com.example.demo.dto.MediaMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MediaService {

    private final String ffprobePath;
    private final ObjectMapper objectMapper;

    public MediaService(@Value("${ffmpeg.path}") String ffmpegPath, ObjectMapper objectMapper) {
        this.ffprobePath = ffmpegPath + (ffmpegPath.endsWith("/") || ffmpegPath.endsWith("\\") ? "" : "\\") + "ffprobe.exe";
        this.objectMapper = objectMapper;
    }

    public MediaMetadata extractMetadata(Path filePath) {
        try {
            List<String> command = new ArrayList<>();
            command.add(ffprobePath);
            command.add("-v");
            command.add("quiet");
            command.add("-print_format");
            command.add("json");
            command.add("-show_format");
            command.add("-show_streams");
            command.add(filePath.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("ffprobe failed with exit code: " + exitCode);
            }

            JsonNode rootNode = objectMapper.readTree(output.toString());
            return parseMetadata(rootNode);

        } catch (Exception e) {
            log.error("Failed to extract metadata: ", e);
            throw new RuntimeException("Invalid or corrupted media file", e);
        }
    }

    private MediaMetadata parseMetadata(JsonNode rootNode) {
        JsonNode formatNode = rootNode.get("format");
        JsonNode streamsNode = rootNode.get("streams");

        MediaMetadata.MediaMetadataBuilder builder = MediaMetadata.builder();
        
        String formatName = "";
        if (formatNode != null) {
            if (formatNode.has("duration")) {
                builder.duration(formatNode.get("duration").asDouble());
            }
            if (formatNode.has("format_name")) {
                formatName = formatNode.get("format_name").asText();
            }
        }

        boolean hasVideo = false;
        boolean hasAudio = false;

        if (streamsNode != null && streamsNode.isArray()) {
            for (JsonNode stream : streamsNode) {
                String codecType = stream.get("codec_type").asText();
                if ("video".equals(codecType) && !hasVideo) {
                    hasVideo = true;
                    // image2, png_pipe etc are image formats in ffprobe
                    if (formatName.contains("image") || formatName.contains("png") || formatName.contains("jpeg") || formatName.contains("jpg")) {
                        builder.type("image");
                    } else {
                        builder.type("video");
                    }
                    builder.codec(stream.get("codec_name").asText());
                    builder.width(stream.get("width").asInt());
                    builder.height(stream.get("height").asInt());
                    
                    if (stream.has("avg_frame_rate")) {
                        String avgFrameRate = stream.get("avg_frame_rate").asText();
                        if (avgFrameRate.contains("/")) {
                            String[] parts = avgFrameRate.split("/");
                            try {
                                double num = Double.parseDouble(parts[0]);
                                double den = Double.parseDouble(parts[1]);
                                builder.fps(den != 0 ? num / den : 0);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                } else if ("audio".equals(codecType) && !hasAudio) {
                    hasAudio = true;
                    if (!hasVideo) {
                        builder.type("audio");
                        builder.codec(stream.get("codec_name").asText());
                    }
                    if (stream.has("sample_rate")) {
                        builder.sampleRate(stream.get("sample_rate").asInt());
                    }
                    if (stream.has("channels")) {
                        builder.channels(stream.get("channels").asInt());
                    }
                    
                    if (!hasVideo && builder.build().getDuration() == null && stream.has("duration")) {
                        builder.duration(stream.get("duration").asDouble());
                    }
                }
            }
        }

        return builder.build();
    }
}
