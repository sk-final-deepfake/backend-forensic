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
        
        if (formatNode != null && formatNode.has("duration")) {
            builder.duration(formatNode.get("duration").asDouble());
        }

        boolean hasVideo = false;
        boolean hasAudio = false;

        if (streamsNode != null && streamsNode.isArray()) {
            for (JsonNode stream : streamsNode) {
                String codecType = stream.get("codec_type").asText();
                if ("video".equals(codecType) && !hasVideo) {
                    hasVideo = true;
                    builder.type("video");
                    builder.codec(stream.get("codec_name").asText());
                    builder.width(stream.get("width").asInt());
                    builder.height(stream.get("height").asInt());
                    
                    String avgFrameRate = stream.get("avg_frame_rate").asText();
                    if (avgFrameRate.contains("/")) {
                        String[] parts = avgFrameRate.split("/");
                        double num = Double.parseDouble(parts[0]);
                        double den = Double.parseDouble(parts[1]);
                        builder.fps(den != 0 ? num / den : 0);
                    }
                } else if ("audio".equals(codecType) && !hasAudio) {
                    hasAudio = true;
                    if (!hasVideo) {
                        builder.type("audio");
                        builder.codec(stream.get("codec_name").asText());
                    }
                    builder.sampleRate(stream.get("sample_rate").asInt());
                    builder.channels(stream.get("channels").asInt());
                    
                    if (!hasVideo && builder.build().getDuration() == null && stream.has("duration")) {
                        builder.duration(stream.get("duration").asDouble());
                    }
                }
            }
        }

        return builder.build();
    }
}
