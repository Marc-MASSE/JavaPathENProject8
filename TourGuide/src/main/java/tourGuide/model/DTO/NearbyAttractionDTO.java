package tourGuide.model.DTO;

import gpsUtil.location.Location;
import lombok.Builder;

@Builder
public class NearbyAttractionDTO {
    private String attractionName;
    private Location attractionLocation;
    private Location userLocation;
    private double distance;
    private int rewardPoints;

    public double getDistance() {
        return distance;
    }
}
