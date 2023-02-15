package tourGuide.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import tourGuide.model.User;
import tourGuide.model.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;

	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * To calculate the rewards that accrue to a user.
	 * An attraction is considered visited if it is close to a visited place.
	 * @param user The one whose reward to calculate.
	 */
	public void calculateRewards(User user) {
		// To create a thread pool
		Executor executor = Executors.newFixedThreadPool(100);
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<Attraction> attractions = gpsUtil.getAttractions();
		for(VisitedLocation visitedLocation : userLocations) {
			for(Attraction attraction : attractions) {
				if(user.getUserRewards()
						.stream()
						.filter(r -> r.attraction.attractionName.equals(attraction.attractionName))
						.count()==0){
							if(nearAttraction(visitedLocation, attraction)) {
								// To connect concurrent actions
								CompletableFuture.supplyAsync(() -> {
									return getRewardPoints(attraction, user);
								},executor).thenAccept(rewardPoints -> {
									user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
								});
							}
						}
			}
		}
	}

	/**
	 * To check if an attraction is near a location or not
	 * An attraction is considered close if its distance is less than attractionProximityRange
	 * @param attraction to get the attraction location
	 * @param location
	 * @return true if the attraction is close, false otherwise
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}

	/**
	 * To check if an attraction is near a user's location or not
	 * An attraction is considered close if its distance is less than proximityBuffer
	 * @param visitedLocation to get the user location
	 * @param attraction to get the attraction location
	 * @return true if the attraction is close, false otherwise
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	/**
	 * To calculate the number of reward points earned by a user by visiting an attraction
	 * @param attraction
	 * @param user
	 * @return this number of reward points
	 */
	public int getRewardPoints(Attraction attraction, User user) {
		// rewardsCentral.getAttractionRewardPoints method is slow.
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());

	}

	/**
	 * To get the distance between two locations
	 * @param loc1
	 * @param loc2
	 * @return this distance
	 */
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
