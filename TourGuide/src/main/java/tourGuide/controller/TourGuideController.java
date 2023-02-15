package tourGuide.controller;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jsoniter.output.JsonStream;

import gpsUtil.location.VisitedLocation;
import tourGuide.service.TourGuideService;
import tourGuide.model.User;
import tripPricer.Provider;

@RestController
public class TourGuideController {

    private final Logger logger = LoggerFactory.getLogger(TourGuideController.class);

	@Autowired
	TourGuideService tourGuideService;
	
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }

    /**
     * To get the location of a user designated by his username
     * @param userName
     * @return a new JSON object that contains user's location (longitude, latitude)
     * @throws ExecutionException will be thrown if interrupt is called on the waiting thread before the computation has completed
     * @throws InterruptedException is thrown when a thread is interrupted while it's waiting, sleeping, or otherwise occupied
     */
    @RequestMapping("/getLocation") 
    public String getLocation(@RequestParam String userName) throws ExecutionException, InterruptedException {
    	VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
        logger.info("GET /getLocation => location of {}",userName);
		return JsonStream.serialize(visitedLocation.location);
    }
    
     /**
     * To get the closest five tourist attractions to the user - no matter how far away they are.
     * @param userName
     * @return a new JSON object that contains:
     *     - attractionName : the name of Tourist attraction
     *     - attractionLocation : the tourist attraction lat/long
     *     - userLocation : the user's location lat/long
     *     - distance : the distance in miles between the user's location and this attraction
     *     - rewardPoints : the reward points for visiting this attraction
     */
    @RequestMapping("/getNearbyAttractions") 
    public String getNearbyAttractions(@RequestParam String userName) throws ExecutionException, InterruptedException {
    	VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
        User user = tourGuideService.getUser(userName);
        logger.info("GET /getNearbyAttractions => list of the closest five tourist attractions to {}",userName);
    	return JsonStream.serialize(tourGuideService.getNearByAttractions(user,visitedLocation));
    }

    /**
     * To get the list of all rewards earned by a user.
     * @param userName
     * @return a JSON list of UserReward that contains :
     *     - an attraction visited
     *     - its location
     *     - its reward points
     */
    @RequestMapping("/getRewards") 
    public String getRewards(@RequestParam String userName) {
        logger.info("GET /getRewards => list of all rewards earned by {}",userName);
        return JsonStream.serialize(tourGuideService.getUserRewards(getUser(userName)));
    }

    /**
     * To get a list of every user's most recent location as JSON
     * @return a JSON mapping of userId to Locations (longitude,latitude)
     * Example : "019b04a9-067a-4c76-8817-ee75088c3822": {"longitude":-48.188821,"latitude":74.84371}
    */
    @RequestMapping("/getAllCurrentLocations")
    public String getAllCurrentLocations() {
    	logger.info("GET /getAllCurrentLocations => list of every user's most recent location");
    	return JsonStream.serialize(tourGuideService.getAllCurrentLocations());
    }

    /**
     * To get all the trip deals of a user.
     * @param userName
     * @return a JSON list of Provider that contains :
     *     - its id
     *     - its name
     *     - its price
     */
    @RequestMapping("/getTripDeals")
    public String getTripDeals(@RequestParam String userName) {
    	List<Provider> providers = tourGuideService.getTripDeals(getUser(userName));
        logger.info("GET /getTripDeals => all the trip deals of {}",userName);
    	return JsonStream.serialize(providers);
    }

    /**
     * To get a user by his userName
     * @param userName
     * @return this user
     */
    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}