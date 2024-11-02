package tukano.api.rest;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import tukano.api.Short;

@Path(RestShorts.PATH)
public interface RestShorts {
	String PATH = "/shorts";
	
	String USER_ID = "userId";
	String USER_ID1 = "userId1";
	String USER_ID2 = "userId2";
	String SHORT_ID = "shortId";
	
	String PWD = "pwd";
	String FEED = "/feed";
	String TOKEN = "token";
	String LIKES = "/likes";
	String SHORTS = "/shorts";
	String FOLLOWERS = "/followers";
	String HAS_CACHE = "hasCache";
	
	@POST
	@Path("/{" + USER_ID + "}")
	@Produces(MediaType.APPLICATION_JSON)
	Short createShort(@PathParam(USER_ID) String userId, @QueryParam(PWD) String password, @QueryParam(HAS_CACHE) boolean hasCache);

	@DELETE
	@Path("/{" + SHORT_ID + "}")
	void deleteShort(@PathParam(SHORT_ID) String shortId, @QueryParam(PWD) String password, @QueryParam(HAS_CACHE) boolean hasCache);

	@GET
	@Path("/{" + SHORT_ID + "}" )
	@Produces(MediaType.APPLICATION_JSON)
	Short getShort(@PathParam(SHORT_ID) String shortId, @QueryParam(HAS_CACHE) boolean hasCache);

	@GET
	@Path("/{" + USER_ID + "}" + SHORTS )
	@Produces(MediaType.APPLICATION_JSON)
	List<String> getShorts(@PathParam(USER_ID) String userId, @QueryParam(HAS_CACHE) boolean hasCache);

	@POST
	@Path("/{" + USER_ID1 + "}/{" + USER_ID2 + "}" + FOLLOWERS )
	@Consumes(MediaType.APPLICATION_JSON)
	void follow(@PathParam(USER_ID1) String userId1, @PathParam(USER_ID2) String userId2, FollowRequest isFollowing, @QueryParam(PWD) String password, @QueryParam(HAS_CACHE) boolean hasCache);

	@GET
	@Path("/{" + USER_ID + "}" + FOLLOWERS )
	@Produces(MediaType.APPLICATION_JSON)
	List<String> followers(@PathParam(USER_ID) String userId, @QueryParam(PWD) String password, @QueryParam(HAS_CACHE) boolean hasCache);

	@POST
	@Path("/{" + SHORT_ID + "}/{" + USER_ID + "}" + LIKES )
	@Consumes(MediaType.APPLICATION_JSON)
	void like(@PathParam(SHORT_ID) String shortId, @PathParam(USER_ID) String userId, LikeRequest isLiked,  @QueryParam(PWD) String password, @QueryParam(HAS_CACHE) boolean hasCache);

	@GET
	@Path("/{" + SHORT_ID + "}" + LIKES )
	@Produces(MediaType.APPLICATION_JSON)
	List<String> likes(@PathParam(SHORT_ID) String shortId, @QueryParam(PWD) String password, @QueryParam(HAS_CACHE) boolean hasCache);

	@GET
	@Path("/{" + USER_ID + "}" + FEED )
	@Produces(MediaType.APPLICATION_JSON)
	List<String> getFeed( @PathParam(USER_ID) String userId, @QueryParam(PWD) String password, @QueryParam(HAS_CACHE) boolean hasCache);
	
	@DELETE
	@Path("/{" + USER_ID + "}" + SHORTS)
	void deleteAllShorts(@PathParam(USER_ID) String userId, @QueryParam(PWD) String password, @QueryParam(TOKEN) String token, @QueryParam(HAS_CACHE) boolean hasCache);

	class FollowRequest {
		@JsonProperty("isFollowing")
		private boolean isFollowing;

		public FollowRequest(){}

		public FollowRequest(boolean isFollowing){
			this.isFollowing = isFollowing;
		}

		public boolean isFollowing() {
			return isFollowing;
		}

		public void setFollowing(boolean isFollowing) {
			this.isFollowing = isFollowing;
		}
	}

	class LikeRequest {
		@JsonProperty("isLiked")
		private boolean isLiked;

		public LikeRequest(){}

		public LikeRequest(boolean isLiked){
			this.isLiked = isLiked;
		}

		public boolean isLiked() {
			return isLiked;
		}

		public void isLiked(boolean isLiked) {
			this.isLiked = isLiked;
		}
	}




}

