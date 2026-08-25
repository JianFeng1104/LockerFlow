package com.lockerflow.service;

import com.lockerflow.dto.request.CreateLockerCellRequest;
import com.lockerflow.dto.response.LockerCellResponse;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.exception.ConflictException;
import com.lockerflow.exception.ResourceNotFoundException;
import com.lockerflow.repository.LockerCellRepository;
import com.lockerflow.repository.LockerStationRepository;
import com.lockerflow.repository.ParcelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LockerCellServiceTests {

    @Mock
    private LockerStationRepository stationRepository;

    @Mock
    private LockerCellRepository cellRepository;

    @Mock
    private ParcelRepository parcelRepository;

    @InjectMocks
    private LockerCellService cellService;

    @Test
    void createsAvailableSmallCell() {
        LockerStation station = activeStation();
        stubCreatableStation(1L, station);

        LockerCellResponse response = cellService.createCell(
                1L,
                new CreateLockerCellRequest("A01", LockerSize.SMALL)
        );

        assertThat(response.size()).isEqualTo(LockerSize.SMALL);
        assertThat(response.status()).isEqualTo(LockerCellStatus.AVAILABLE);
    }

    @Test
    void normalizesCellCodeToUppercase() {
        stubCreatableStation(1L, activeStation());

        LockerCellResponse response = cellService.createCell(
                1L,
                new CreateLockerCellRequest(" a01 ", LockerSize.SMALL)
        );

        assertThat(response.cellCode()).isEqualTo("A01");
    }

    @Test
    void rejectsDuplicateCellCodeIgnoringCase() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(activeStation()));
        when(cellRepository.existsByStationIdAndCellCodeIgnoreCase(1L, "A01")).thenReturn(true);

        assertThatThrownBy(() -> cellService.createCell(
                1L,
                new CreateLockerCellRequest("a01", LockerSize.SMALL)
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Locker cell A01 already exists in this station");

        verify(cellRepository, never()).save(any());
    }

    @Test
    void allowsSameCellCodeAtDifferentStations() {
        LockerStation first = activeStation();
        LockerStation second = new LockerStation("Station B", "Block B");
        when(stationRepository.findById(1L)).thenReturn(Optional.of(first));
        when(stationRepository.findById(2L)).thenReturn(Optional.of(second));
        when(cellRepository.save(any(LockerCell.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LockerCellResponse firstResponse = cellService.createCell(
                1L,
                new CreateLockerCellRequest("A01", LockerSize.SMALL)
        );
        LockerCellResponse secondResponse = cellService.createCell(
                2L,
                new CreateLockerCellRequest("A01", LockerSize.MEDIUM)
        );

        assertThat(firstResponse.cellCode()).isEqualTo(secondResponse.cellCode());
        verify(cellRepository).existsByStationIdAndCellCodeIgnoreCase(1L, "A01");
        verify(cellRepository).existsByStationIdAndCellCodeIgnoreCase(2L, "A01");
    }

    @Test
    void reportsMissingStationDuringCreation() {
        when(stationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cellService.createCell(
                99L,
                new CreateLockerCellRequest("A01", LockerSize.SMALL)
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsCreationAtDisabledStation() {
        LockerStation station = activeStation();
        station.changeStatus(LockerStationStatus.DISABLED);
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));

        assertThatThrownBy(() -> cellService.createCell(
                1L,
                new CreateLockerCellRequest("A01", LockerSize.SMALL)
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Cannot add locker cells to a disabled station");
    }

    @Test
    void changesAvailableCellToMaintenance() {
        LockerCell cell = cell(LockerCellStatus.AVAILABLE);
        stubOwnedCell(cell);

        LockerCellResponse response = cellService.changeCellStatus(1L, 10L, LockerCellStatus.MAINTENANCE);

        assertThat(response.status()).isEqualTo(LockerCellStatus.MAINTENANCE);
    }

    @Test
    void changesMaintenanceCellToAvailable() {
        LockerCell cell = cell(LockerCellStatus.MAINTENANCE);
        stubOwnedCell(cell);

        LockerCellResponse response = cellService.changeCellStatus(1L, 10L, LockerCellStatus.AVAILABLE);

        assertThat(response.status()).isEqualTo(LockerCellStatus.AVAILABLE);
    }

    @Test
    void changesAvailableCellToDisabled() {
        LockerCell cell = cell(LockerCellStatus.AVAILABLE);
        stubOwnedCell(cell);

        LockerCellResponse response = cellService.changeCellStatus(1L, 10L, LockerCellStatus.DISABLED);

        assertThat(response.status()).isEqualTo(LockerCellStatus.DISABLED);
    }

    @Test
    void rejectsOccupiedCellToMaintenance() {
        LockerCell cell = cell(LockerCellStatus.OCCUPIED);
        stubOwnedCell(cell);

        assertThatThrownBy(() -> cellService.changeCellStatus(
                1L,
                10L,
                LockerCellStatus.MAINTENANCE
        )).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsOccupiedCellToDisabled() {
        LockerCell cell = cell(LockerCellStatus.OCCUPIED);
        stubOwnedCell(cell);

        assertThatThrownBy(() -> cellService.changeCellStatus(
                1L,
                10L,
                LockerCellStatus.DISABLED
        )).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsManualAvailableToOccupied() {
        LockerCell cell = cell(LockerCellStatus.AVAILABLE);
        stubOwnedCell(cell);

        assertThatThrownBy(() -> cellService.changeCellStatus(
                1L,
                10L,
                LockerCellStatus.OCCUPIED
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be set manually");
    }

    @Test
    void rejectsManualOccupiedToAvailable() {
        LockerCell cell = cell(LockerCellStatus.OCCUPIED);
        stubOwnedCell(cell);

        assertThatThrownBy(() -> cellService.changeCellStatus(
                1L,
                10L,
                LockerCellStatus.AVAILABLE
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("occupied locker cell");
    }

    @Test
    void rejectsCellThatDoesNotBelongToUrlStation() {
        when(cellRepository.findByIdAndStationId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cellService.getCell(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("station 1");
    }

    @Test
    void rejectsManualChangeWhenActiveParcelAssociationExists() {
        LockerCell cell = cell(LockerCellStatus.AVAILABLE);
        stubOwnedCell(cell);
        when(parcelRepository.existsByLockerCellIdAndStatusIn(any(), anySet())).thenReturn(true);

        assertThatThrownBy(() -> cellService.changeCellStatus(
                1L,
                10L,
                LockerCellStatus.MAINTENANCE
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("active parcel");
    }

    private LockerStation activeStation() {
        return new LockerStation("Station A", "Block A Lobby");
    }

    private LockerCell cell(LockerCellStatus status) {
        LockerCell cell = new LockerCell(activeStation(), "A01", LockerSize.SMALL);
        cell.changeStatus(status);
        return cell;
    }

    private void stubCreatableStation(Long stationId, LockerStation station) {
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(cellRepository.save(any(LockerCell.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubOwnedCell(LockerCell cell) {
        when(cellRepository.findByIdAndStationId(10L, 1L)).thenReturn(Optional.of(cell));
    }
}

