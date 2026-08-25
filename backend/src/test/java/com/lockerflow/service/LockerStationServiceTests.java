package com.lockerflow.service;

import com.lockerflow.dto.request.CreateLockerStationRequest;
import com.lockerflow.dto.request.UpdateLockerStationRequest;
import com.lockerflow.dto.response.LockerStationResponse;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.enums.LockerCellStatus;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LockerStationServiceTests {

    @Mock
    private LockerStationRepository stationRepository;

    @Mock
    private LockerCellRepository cellRepository;

    @Mock
    private ParcelRepository parcelRepository;

    @InjectMocks
    private LockerStationService stationService;

    @Test
    void createsActiveStation() {
        when(stationRepository.save(any(LockerStation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LockerStationResponse response = stationService.createStation(
                new CreateLockerStationRequest("KL Sentral Locker", "KL Sentral, Kuala Lumpur")
        );

        assertThat(response.name()).isEqualTo("KL Sentral Locker");
        assertThat(response.status()).isEqualTo(LockerStationStatus.ACTIVE);
        assertThat(response.totalCells()).isZero();
    }

    @Test
    void rejectsBlankStationNameAtServiceBoundary() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> stationService.createStation(
                        new CreateLockerStationRequest(" ", "KL Sentral")
                ))
                .withMessageContaining("name");

        verify(stationRepository, never()).save(any());
    }

    @Test
    void reportsMissingStation() {
        when(stationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stationService.getStation(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updatesStationDetailsThroughEntityMethod() {
        LockerStation station = station();
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(cellRepository.findByStationIdOrderByCellCodeAsc(1L)).thenReturn(List.of());

        LockerStationResponse response = stationService.updateStation(
                1L,
                new UpdateLockerStationRequest("Station B", "Block B Lobby")
        );

        assertThat(response.name()).isEqualTo("Station B");
        assertThat(response.address()).isEqualTo("Block B Lobby");
    }

    @Test
    void changesActiveStationToMaintenanceWhenNoOccupancyExists() {
        LockerStation station = station();
        stubStation(station);

        LockerStationResponse response = stationService.changeStationStatus(
                1L,
                LockerStationStatus.MAINTENANCE
        );

        assertThat(response.status()).isEqualTo(LockerStationStatus.MAINTENANCE);
    }

    @Test
    void rejectsMaintenanceWhenOccupiedCellExists() {
        LockerStation station = station();
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(cellRepository.existsByStationIdAndStatus(1L, LockerCellStatus.OCCUPIED)).thenReturn(true);

        assertThatThrownBy(() -> stationService.changeStationStatus(
                1L,
                LockerStationStatus.MAINTENANCE
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Station cannot enter maintenance while occupied cells exist");

        assertThat(station.getStatus()).isEqualTo(LockerStationStatus.ACTIVE);
    }

    @Test
    void rejectsDisabledWhenOccupiedCellExists() {
        LockerStation station = station();
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(cellRepository.existsByStationIdAndStatus(1L, LockerCellStatus.OCCUPIED)).thenReturn(true);

        assertThatThrownBy(() -> stationService.changeStationStatus(
                1L,
                LockerStationStatus.DISABLED
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Station cannot be disabled while occupied cells exist");
    }

    @Test
    void changesMaintenanceStationBackToActive() {
        LockerStation station = station();
        station.changeStatus(LockerStationStatus.MAINTENANCE);
        stubStation(station);

        LockerStationResponse response = stationService.changeStationStatus(1L, LockerStationStatus.ACTIVE);

        assertThat(response.status()).isEqualTo(LockerStationStatus.ACTIVE);
    }

    @Test
    void changesDisabledStationBackToActive() {
        LockerStation station = station();
        station.changeStatus(LockerStationStatus.DISABLED);
        stubStation(station);

        LockerStationResponse response = stationService.changeStationStatus(1L, LockerStationStatus.ACTIVE);

        assertThat(response.status()).isEqualTo(LockerStationStatus.ACTIVE);
    }

    @Test
    void rejectsMaintenanceWhenActiveParcelRevealsInconsistentCellState() {
        LockerStation station = station();
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(cellRepository.existsByStationIdAndStatus(1L, LockerCellStatus.OCCUPIED)).thenReturn(false);
        when(parcelRepository.existsByLockerCellStationIdAndStatusIn(anyLong(), anySet())).thenReturn(true);

        assertThatThrownBy(() -> stationService.changeStationStatus(
                1L,
                LockerStationStatus.MAINTENANCE
        )).isInstanceOf(ConflictException.class);
    }

    private LockerStation station() {
        return new LockerStation("Station A", "Block A Lobby");
    }

    private void stubStation(LockerStation station) {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(cellRepository.findByStationIdOrderByCellCodeAsc(any())).thenReturn(List.of());
    }
}
