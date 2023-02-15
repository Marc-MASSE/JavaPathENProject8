package tourGuide.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tourGuide.helper.InternalTestHelper;
import tourGuide.model.DTO.AttractionDistanceDTO;
import tourGuide.model.DTO.NearbyAttractionDTO;
import tourGuide.tracker.Tracker;
import tourGuide.model.User;
import tourGuide.model.UserReward;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;

	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;
	
	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
		if(testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}
	
	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	/**
	 * To get the last visited location of a user designated by his username
	 * @param user
	 * @return a visitedLocation
	 * @throws ExecutionException will be thrown if interrupt is called on the waiting thread before the computation has completed
	 * @throws InterruptedException is thrown when a thread is interrupted while it's waiting, sleeping, or otherwise occupied
	 */
	public VisitedLocation getUserLocation(User user) throws ExecutionException, InterruptedException {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ?
			user.getLastVisitedLocation() :
			trackUserLocation(user).get();
		return visitedLocation;
	}

	/**
	 * Get a list of every user's most recent location
	 * @return a map of userId to Locations (longitude,latitude)
	 */
	public Map<String,Location> getAllCurrentLocations(){
		Map<String,Location> allCurrentLocations = new HashMap<>();
		List<User> users = getAllUsers();
		users.forEach(u -> {
			allCurrentLocations.put(u.getUserId().toString(),u.getLastVisitedLocation().location);
		});
		return allCurrentLocations;
	}

	/**
	 * To get a user designated by his username
	 * @param userName
	 * @return this user
	 */
	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	/**
	 * To get the list of all users
	 * @return a Map that contains (userName, user)
	 */
	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	/**
	 * To add a user that does not already exist
	 * @param user
	 */
	public void addUser(User user) {
		if(!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	/**
	 * To get all the trip deals of a user.
	 * @param user
	 * @return a list of Provider that contains :
	 *     - its id
	 *     - its name
	 *     - its price
	 */
	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(), user.getUserPreferences().getNumberOfAdults(), 
				user.getUserPreferences().getNumberOfChildren(), user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * To add the current location of a user to his visitedLocation list
	 * 	and to add its reward
	 * @param user
	 * @return a completableFuture of visitedLocation
	 */
	public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
		// To create a thread pool
		Executor executor = Executors.newFixedThreadPool(100);

		// gpsUtil.getUserLocation is slow.
		CompletableFuture<VisitedLocation> visitedLocation = CompletableFuture.supplyAsync(() -> {
			return gpsUtil.getUserLocation(user.getUserId());
				},executor)
				.thenApply(location ->{
					user.addToVisitedLocations(location);
					rewardsService.calculateRewards(user);
					return location;
				});
		return visitedLocation;
	}

	/**
	 * To get the closest five tourist attractions to the user - no matter how far away they are.
	 * @param user
	 * @param visitedLocation
	 * @return a new JSON object that contains:
	 * 	 - attractionName : the name of Tourist attraction
	 * 	 - attractionLocation : the tourist attraction lat/long
	 * 	 - userLocation : the user's location lat/long
	 * 	 - distance : the distance in miles between the user's location and this attraction
	 * 	 - rewardPoints : the reward points for visiting this attraction
	 */
	public List<NearbyAttractionDTO> getNearByAttractions(User user, VisitedLocation visitedLocation) {
		// to solve problem between French and English number format
		// Example : "-79,792443" <> "-79.792443"
		Locale.setDefault(Locale.ENGLISH);

		List<NearbyAttractionDTO> nearbyAttractions = new ArrayList<>();
		List<AttractionDistanceDTO> fiveNearestAttractions = getFiveNearestAttractions(visitedLocation);
		fiveNearestAttractions.forEach(f -> {
			Location attractionLocation = new Location(f.getAttraction().latitude,f.getAttraction().longitude);
			NearbyAttractionDTO attractionDTO = NearbyAttractionDTO.builder()
					.attractionName(f.getAttraction().attractionName)
					.attractionLocation(new Location(f.getAttraction().latitude,f.getAttraction().longitude))
					.userLocation(visitedLocation.location)
					.distance(f.getDistance())
					.rewardPoints(rewardsService.getRewardPoints(f.getAttraction(),user))
					.build();
			nearbyAttractions.add(attractionDTO);
		});
		return nearbyAttractions;
	}

	/**
	 * To get the closest five tourist attractions to the user - no matter how far away they are.
	 * @param visitedLocation
	 * @return a list of five AttractionDistanceDTO that contains :
	 * 	- attraction : an attractions with their distances between the user's location and each attraction
	 * 	- distance : its distance from the user's location
	 */
	public List<AttractionDistanceDTO> getFiveNearestAttractions(VisitedLocation visitedLocation) {
		List<Attraction> allAttractions = gpsUtil.getAttractions();
		List<AttractionDistanceDTO> attractionDistances = new ArrayList<>();
		allAttractions.forEach(a -> {
			AttractionDistanceDTO attractionDistanceDTO = AttractionDistanceDTO.builder()
					.attraction(a)
					.distance(rewardsService.getDistance(visitedLocation.location,a))
					.build();
			attractionDistances.add(attractionDistanceDTO);
		});
		return attractionDistances.stream()
				.sorted(Comparator.comparingDouble(AttractionDistanceDTO::getDistance))
				.limit(5)
				.collect(Collectors.toList());
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() { 
		      public void run() {
		        tracker.stopTracking();
		      } 
		    }); 
	}
	
	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	/**
	 * To create a list of users
	 * Their number is determined by InternalTestHelper.getInternalUserNumber()
	 */
	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);
			
			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	/**
	 * To add 3 random visitedLocation to a user
	 * @param user
	 */
	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i-> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(), new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}
	
	private double generateRandomLongitude() {
		double leftLimit = -180;
	    double rightLimit = 180;
	    return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}
	
	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
	    double rightLimit = 85.05112878;
	    return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}
	
	public Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
	    return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}
	
}
