package com.acetelecom.repairdesk.feature.location.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.location.domain.ServiceLocation;
import com.acetelecom.repairdesk.feature.location.domain.TechnicianPresence;
import com.acetelecom.repairdesk.feature.location.domain.VehicleType;
import com.acetelecom.repairdesk.feature.location.dto.LocationBoardResponse;
import com.acetelecom.repairdesk.feature.location.dto.LocationBoardResponse.JobMarker;
import com.acetelecom.repairdesk.feature.location.dto.LocationBoardResponse.Shop;
import com.acetelecom.repairdesk.feature.location.dto.LocationBoardResponse.TechnicianMarker;
import com.acetelecom.repairdesk.feature.location.repository.TechnicianPresenceRepository;
import com.acetelecom.repairdesk.feature.location.service.MapsService.RouteEstimate;
import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.repository.BusinessSettingsRepository;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

@Service
public class LocationBoardService {

    private final BusinessSettingsRepository businessRepo;
    private final UserRepository userRepo;
    private final TechnicianPresenceRepository presenceRepo;
    private final JobRepository jobRepo;
    private final MapsService mapsService;

    public LocationBoardService(BusinessSettingsRepository businessRepo, UserRepository userRepo,
            TechnicianPresenceRepository presenceRepo, JobRepository jobRepo, MapsService mapsService) {
        this.businessRepo = businessRepo;
        this.userRepo = userRepo;
        this.presenceRepo = presenceRepo;
        this.jobRepo = jobRepo;
        this.mapsService = mapsService;
    }

    @Transactional
    public LocationBoardResponse board() {
        UUID companyId = CompanyContext.get();
        BusinessSettings business = (companyId == null
                ? businessRepo.findById((short) 1)
                : businessRepo.findByCompanyId(companyId)).orElseThrow();
        Shop shop = new Shop(business.getShopLat(), business.getShopLng(), business.getGeofenceRadiusM());

        List<User> technicians = userRepo.findActiveByRole(Role.TECHNICIAN);
        Map<UUID, TechnicianPresence> presenceByUser = new HashMap<>();
        for (TechnicianPresence row : presenceRepo.findAll()) {
            presenceByUser.put(row.getUserId(), row);
        }

        List<TechnicianMarker> markers = new ArrayList<>();
        Map<UUID, User> technicianById = new HashMap<>();
        for (User technician : technicians) {
            technicianById.put(technician.getId(), technician);
            TechnicianPresence presence = presenceByUser.get(technician.getId());
            markers.add(new TechnicianMarker(
                    technician.getId(),
                    technician.getName(),
                    presence == null ? null : presence.getLat(),
                    presence == null ? null : presence.getLng(),
                    presence == null ? null : presence.getRecordedAt(),
                    presence == null ? null : presence.getJobId(),
                    presence == null || presence.getState() == null ? null : presence.getState().name()));
        }

        List<JobMarker> jobs = new ArrayList<>();
        for (Job job : jobRepo.findOpenWithServicePin()) {
            jobs.add(toJobMarker(job, shop, technicianById, presenceByUser));
        }
        return new LocationBoardResponse(shop, markers, jobs);
    }

    private JobMarker toJobMarker(Job job, Shop shop, Map<UUID, User> technicianById,
            Map<UUID, TechnicianPresence> presenceByUser) {
        ServiceLocation location = job.getServiceLocation();
        User technician = job.getAssignedTechnician();
        String address = addressOf(location);
        Integer etaMinutes = null;
        Integer distanceMetres = null;
        boolean estimated = false;
        String etaFrom = "NONE";

        Double fromLat = null;
        Double fromLng = null;
        String mode = "TWO_WHEELER";
        if (technician != null) {
            TechnicianPresence presence = presenceByUser.get(technician.getId());
            if (presence != null) {
                fromLat = presence.getLat();
                fromLng = presence.getLng();
                etaFrom = "TECHNICIAN";
                User known = technicianById.get(technician.getId());
                if (known != null && known.getVehicleType() == VehicleType.FOUR_WHEELER) {
                    mode = "DRIVE";
                }
            }
        }
        if (fromLat == null && shop.latitude() != null && shop.longitude() != null) {
            fromLat = shop.latitude();
            fromLng = shop.longitude();
            etaFrom = "OFFICE";
        }
        if (fromLat != null && location.getLatitude() != null && location.getLongitude() != null) {
            RouteEstimate route = mapsService.route(
                    fromLat, fromLng, location.getLatitude(), location.getLongitude(), mode);
            distanceMetres = route.distanceMetres();
            etaMinutes = route.durationSeconds() == null
                    ? null
                    : Math.max(1, (int) Math.round(route.durationSeconds() / 60d));
            estimated = route.estimated();
        }

        return new JobMarker(
                job.getId(),
                job.getReferenceNumber(),
                address,
                location.getLatitude(),
                location.getLongitude(),
                technician == null ? null : technician.getId(),
                technician == null ? null : technician.getName(),
                etaMinutes,
                distanceMetres,
                estimated,
                etaFrom);
    }

    private static String addressOf(ServiceLocation location) {
        List<String> parts = new ArrayList<>();
        if (notBlank(location.getAddressLine())) parts.add(location.getAddressLine().trim());
        if (notBlank(location.getLandmark())) parts.add(location.getLandmark().trim());
        if (notBlank(location.getCity())) parts.add(location.getCity().trim());
        if (notBlank(location.getPincode())) parts.add(location.getPincode().trim());
        return String.join(", ", parts);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
