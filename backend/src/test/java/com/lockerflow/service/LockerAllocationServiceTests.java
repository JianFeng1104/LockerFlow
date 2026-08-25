package com.lockerflow.service;

import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.exception.ConflictException;
import com.lockerflow.repository.LockerCellRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LockerAllocationServiceTests {

    @Mock
    private LockerCellRepository cellRepository;

    @InjectMocks
    private LockerAllocationService allocationService;

    @Test
    void allocatesSmallCellForSmallParcel() {
        LockerStation station = activeStation();
        LockerCell small = cell(station, "A01", LockerSize.SMALL);
        stubAvailable(station, LockerSize.SMALL, small);

        assertThat(allocationService.allocate(station, LockerSize.SMALL)).isSameAs(small);
    }

    @Test
    void fallsBackToMediumForSmallParcel() {
        LockerStation station = activeStation();
        LockerCell medium = cell(station, "A02", LockerSize.MEDIUM);
        stubAvailable(station, LockerSize.MEDIUM, medium);

        assertThat(allocationService.allocate(station, LockerSize.SMALL)).isSameAs(medium);
    }

    @Test
    void fallsBackToLargeForSmallParcel() {
        LockerStation station = activeStation();
        LockerCell large = cell(station, "A03", LockerSize.LARGE);
        stubAvailable(station, LockerSize.LARGE, large);

        assertThat(allocationService.allocate(station, LockerSize.SMALL)).isSameAs(large);
    }

    @Test
    void prefersMediumOverLargeForMediumParcel() {
        LockerStation station = activeStation();
        LockerCell medium = cell(station, "A02", LockerSize.MEDIUM);
        stubAvailable(station, LockerSize.MEDIUM, medium);

        assertThat(allocationService.allocate(station, LockerSize.MEDIUM)).isSameAs(medium);
        verify(cellRepository, never()).findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
                station.getId(), LockerCellStatus.AVAILABLE, LockerSize.LARGE
        );
    }

    @Test
    void neverUsesSmallForMediumParcel() {
        LockerStation station = activeStation();

        assertThatThrownBy(() -> allocationService.allocate(station, LockerSize.MEDIUM))
                .isInstanceOf(ConflictException.class);
        verify(cellRepository, never()).findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
                station.getId(), LockerCellStatus.AVAILABLE, LockerSize.SMALL
        );
    }

    @Test
    void fallsBackToLargeForMediumParcel() {
        LockerStation station = activeStation();
        LockerCell large = cell(station, "A03", LockerSize.LARGE);
        stubAvailable(station, LockerSize.LARGE, large);

        assertThat(allocationService.allocate(station, LockerSize.MEDIUM)).isSameAs(large);
    }

    @Test
    void allocatesLargeCellForLargeParcel() {
        LockerStation station = activeStation();
        LockerCell large = cell(station, "A03", LockerSize.LARGE);
        stubAvailable(station, LockerSize.LARGE, large);

        assertThat(allocationService.allocate(station, LockerSize.LARGE)).isSameAs(large);
    }

    @Test
    void largeParcelNeverUsesSmallerCells() {
        LockerStation station = activeStation();

        assertThatThrownBy(() -> allocationService.allocate(station, LockerSize.LARGE))
                .isInstanceOf(ConflictException.class);
        verify(cellRepository, never()).findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
                station.getId(), LockerCellStatus.AVAILABLE, LockerSize.SMALL
        );
        verify(cellRepository, never()).findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
                station.getId(), LockerCellStatus.AVAILABLE, LockerSize.MEDIUM
        );
    }

    @Test
    void ignoresMaintenanceCells() {
        assertUnavailableCellIsIgnored(LockerCellStatus.MAINTENANCE);
    }

    @Test
    void ignoresDisabledCells() {
        assertUnavailableCellIsIgnored(LockerCellStatus.DISABLED);
    }

    @Test
    void ignoresOccupiedCells() {
        assertUnavailableCellIsIgnored(LockerCellStatus.OCCUPIED);
    }

    @Test
    void choosesFirstCellFromRepositoryCodeOrder() {
        LockerStation station = activeStation();
        LockerCell first = cell(station, "A01", LockerSize.SMALL);
        LockerCell third = cell(station, "A03", LockerSize.SMALL);
        LockerCell fifth = cell(station, "A05", LockerSize.SMALL);
        when(cellRepository.findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
                station.getId(), LockerCellStatus.AVAILABLE, LockerSize.SMALL
        )).thenReturn(List.of(first, third, fifth));

        assertThat(allocationService.allocate(station, LockerSize.SMALL)).isSameAs(first);
    }

    @Test
    void rejectsWhenNoSuitableCellIsAvailable() {
        LockerStation station = activeStation();

        assertThatThrownBy(() -> allocationService.allocate(station, LockerSize.MEDIUM))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No suitable locker cell is available for parcel size MEDIUM");
    }

    @Test
    void rejectsNonActiveStationBeforeQueryingCells() {
        LockerStation station = station(LockerStationStatus.MAINTENANCE);

        assertThatThrownBy(() -> allocationService.allocate(station, LockerSize.SMALL))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Locker station is not available for parcel storage");
        verify(cellRepository, never()).findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
                station.getId(), LockerCellStatus.AVAILABLE, LockerSize.SMALL
        );
    }

    private void assertUnavailableCellIsIgnored(LockerCellStatus status) {
        LockerStation station = activeStation();
        LockerCell unavailable = cell(station, "A01", LockerSize.LARGE);
        unavailable.changeStatus(status);

        assertThatThrownBy(() -> allocationService.allocate(station, LockerSize.LARGE))
                .isInstanceOf(ConflictException.class);
        assertThat(unavailable.getStatus()).isEqualTo(status);
        verify(cellRepository).findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
                station.getId(), LockerCellStatus.AVAILABLE, LockerSize.LARGE
        );
    }

    private void stubAvailable(LockerStation station, LockerSize size, LockerCell... cells) {
        Long stationId = station.getId();
        when(cellRepository.findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
                eq(stationId), eq(LockerCellStatus.AVAILABLE), any(LockerSize.class)
        )).thenAnswer(invocation -> invocation.getArgument(2) == size ? List.of(cells) : List.of());
    }

    private LockerStation activeStation() {
        return station(LockerStationStatus.ACTIVE);
    }

    private LockerStation station(LockerStationStatus status) {
        LockerStation station = mock(LockerStation.class);
        when(station.getId()).thenReturn(1L);
        when(station.getStatus()).thenReturn(status);
        return station;
    }

    private LockerCell cell(LockerStation station, String code, LockerSize size) {
        return new LockerCell(station, code, size);
    }
}
