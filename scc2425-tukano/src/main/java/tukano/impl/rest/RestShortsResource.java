package tukano.impl.rest;

import java.util.List;

import jakarta.inject.Singleton;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.rest.RestShorts;
import tukano.impl.JavaShorts;

@Singleton
public class RestShortsResource extends RestResource implements RestShorts {

	static final Shorts impl = JavaShorts.getInstance();
		
	@Override
	public Short createShort(String userId, String password, boolean hasCache) {
		return super.resultOrThrow( impl.createShort(userId, password, hasCache));
	}

	@Override
	public void deleteShort(String shortId, String password, boolean hasCache) {
		super.resultOrThrow( impl.deleteShort(shortId, password, hasCache));
	}

	@Override
	public Short getShort(String shortId, boolean hasCache) {
		return super.resultOrThrow( impl.getShort(shortId, hasCache));
	}
	@Override
	public List<String> getShorts(String userId, boolean hasCache) {
		return super.resultOrThrow( impl.getShorts(userId, hasCache));
	}

	@Override
	public void follow(String userId1, String userId2, FollowRequest isFollowingC, String password, boolean hasCache) {
		boolean isFollowing = isFollowingC.isFollowing();
		super.resultOrThrow( impl.follow(userId1, userId2, isFollowing, password, hasCache));
	}

	@Override
	public List<String> followers(String userId, String password, boolean hasCache) {
		return super.resultOrThrow( impl.followers(userId, password, hasCache));
	}

	@Override
	public void like(String shortId, String userId, LikeRequest isLikedC, String password, boolean hasCache) {
		boolean isLiked = isLikedC.isLiked();
		super.resultOrThrow( impl.like(shortId, userId, isLiked, password, hasCache));
	}

	@Override
	public List<String> likes(String shortId, String password, boolean hasCache) {
		return super.resultOrThrow( impl.likes(shortId, password, hasCache));
	}

	@Override
	public List<String> getFeed(String userId, String password, boolean hasCache) {
		return super.resultOrThrow( impl.getFeed(userId, password, hasCache));
	}

	@Override
	public void deleteAllShorts(String userId, String password, String token, boolean hasCache) {
		super.resultOrThrow( impl.deleteAllShorts(userId, password, token, hasCache));
	}	
}
