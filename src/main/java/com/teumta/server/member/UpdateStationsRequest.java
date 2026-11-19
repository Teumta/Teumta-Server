package com.teumta.server.member;

import jakarta.validation.constraints.Size;

// 집역·회사역을 함께 보낸다. null이면 그 역을 지운다(고르지 않은 상태)
public record UpdateStationsRequest(
        @Size(max = 20, message = "역 ID가 너무 길어요.") String homeStationId,
        @Size(max = 20, message = "역 ID가 너무 길어요.") String workStationId
) {
}
