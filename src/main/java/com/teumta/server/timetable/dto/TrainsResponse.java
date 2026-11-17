package com.teumta.server.timetable.dto;

import com.teumta.server.timetable.DayType;

import java.time.LocalDate;
import java.util.List;

// 한 요일 종류의 시간표 전체
// baseDate: 시간표를 받은 날. 앱이 "몇 월 며칠 시간표 기준"으로 보여 준다
public record TrainsResponse(LocalDate baseDate, DayType dayType, List<TrainResponse> trains) {
}
