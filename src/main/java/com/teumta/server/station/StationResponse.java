package com.teumta.server.station;

// 역 한 개의 응답. 엔티티를 그대로 내보내지 않고 앱에 필요한 값만 담는다
public record StationResponse(
        String id,
        String name,
        String branch,
        int order,
        String parentId,
        boolean expressStop,
        Integer transferSec
) {
    public static StationResponse from(Station station) {
        // parent는 LAZY라 프록시(빈 껍데기)지만, ID는 껍데기에도 들어 있어서 추가 쿼리 없이 읽힌다
        Station parent = station.getParent();
        return new StationResponse(
                station.getId(),
                station.getName(),
                station.getBranch(),
                station.getSortOrder(),
                parent == null ? null : parent.getId(),
                station.isExpressStop(),
                station.getTransferSec()
        );
    }
}
