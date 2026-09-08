package com.team1.expo.reservation.service;

import com.team1.expo.client.ReservationClient;
import com.team1.expo.client.ReservationClient.AttendeeItem;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.ExpoRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendeeExcelService {

    private static final String[] HEADERS = {
            "예약번호", "회차ID", "성명", "연락처", "인원", "금액", "상태", "예약일시"
    };

    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;
    private final ReservationClient reservationClient;

    public byte[] generateExcel(Long requesterId, Long expoId, Long roundId) {
        verifyOwnership(expoId, requesterId);

        List<AttendeeItem> attendees = reservationClient.getAttendees(expoId, roundId);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("참석자");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int rowNum = 1;
            for (AttendeeItem a : attendees) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(a.reservationNo());
                row.createCell(1).setCellValue(a.roundId());
                row.createCell(2).setCellValue(a.contactName());
                row.createCell(3).setCellValue(a.contactPhone());
                row.createCell(4).setCellValue(a.headcount());
                row.createCell(5).setCellValue(a.amount());
                row.createCell(6).setCellValue(a.status());
                row.createCell(7).setCellValue(a.createdAt());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private void verifyOwnership(Long expoId, Long requesterId) {
        Long ownerId = expoRepository.findById(expoId)
                .flatMap(expo -> channelRepository.findById(expo.getChannelId()))
                .map(ch -> ch.getOwnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!ownerId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
