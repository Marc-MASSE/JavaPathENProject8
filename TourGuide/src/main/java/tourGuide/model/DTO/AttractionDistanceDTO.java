package tourGuide.model.DTO;

import gpsUtil.location.Attraction;
import lombok.Builder;

@Builder
public class AttractionDistanceDTO {
    private Attraction attraction;
    private double distance;

    public Attraction getAttraction() {
        return attraction;
    }
    public double getDistance() {
        return distance;
    }
}
