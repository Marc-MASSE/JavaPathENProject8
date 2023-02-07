package tourGuide;

import static org.junit.Assert.*;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import gpsUtil.location.Location;
import org.junit.Ignore;
import org.junit.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import tourGuide.helper.InternalTestHelper;
import tourGuide.service.RewardsService;
import tourGuide.service.TourGuideService;
import tourGuide.model.User;
import tourGuide.model.UserReward;

public class TestRewardsService {

	@Test
	public void userGetRewards() {

		// to solve problem between French and English number format
		// Example : "-79,792443" <> "-79.792443"
		Locale.setDefault(Locale.ENGLISH);

		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);
		
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		Attraction attraction = gpsUtil.getAttractions().get(0);
		user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));
		tourGuideService.trackUserLocation(user);
		List<UserReward> userRewards = user.getUserRewards();
		tourGuideService.tracker.stopTracking();
		assertTrue(userRewards.size() == 1);
	}
	
	@Test
	public void isWithinAttractionProximity() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		Attraction attraction = gpsUtil.getAttractions().get(0);
		assertTrue(rewardsService.isWithinAttractionProximity(attraction, attraction));
	}
	
	// TODO Needs fixed - can throw ConcurrentModificationException
	@Test
	public void nearAllAttractions() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		rewardsService.setProximityBuffer(Integer.MAX_VALUE);

		InternalTestHelper.setInternalUserNumber(1);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User userTest = tourGuideService.getAllUsers().get(0);
		/*
		userTest.clearVisitedLocations();
		// Add visited location near Disneyland
		userTest.addToVisitedLocations(
				new VisitedLocation(userTest.getUserId(),
				new Location(33.8, -117.9),
				tourGuideService.getRandomTime()));
		// Add visited location near Jackson Hole
		userTest.addToVisitedLocations(
				new VisitedLocation(userTest.getUserId(),
				new Location(43.5, -110.8),
				tourGuideService.getRandomTime()));
		// Add visited location near Mojave National Preserve
		userTest.addToVisitedLocations(
				new VisitedLocation(userTest.getUserId(),
				new Location(35.1, -115.5),
				tourGuideService.getRandomTime()));
		*/

		rewardsService.calculateRewards(userTest);
		List<UserReward> userRewards = tourGuideService.getUserRewards(userTest);
		tourGuideService.tracker.stopTracking();

		//for test
		List<Attraction> attractions = gpsUtil.getAttractions();

		assertEquals(gpsUtil.getAttractions().size(), userRewards.size());
		//assertEquals(userTest.getVisitedLocations().size(), userRewards.size());

	}
	
}
