package tukano.rest;

import java.util.List;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.rest.RestShorts;

public class RestShortsClient extends RestClient implements Shorts{

	public RestShortsClient(String serverURI) {
		super(serverURI, RestShorts.PATH);
	}

	public Result<Short> _createShort(String userId, String password, boolean hasCache) {
		return super.toJavaResult(
				target
				.path(userId)
				.queryParam(RestShorts.PWD, password )
				.queryParam(RestShorts.HAS_CACHE, hasCache)
				.request()
				.accept(MediaType.APPLICATION_JSON)
				.post( Entity.json(null)), Short.class);
	}

	public Result<Void> _deleteShort(String shortId, String password, boolean hasCache) {
		return super.toJavaResult(
				target
				.path(shortId)
				.queryParam(RestShorts.PWD, password )
				.queryParam(RestShorts.HAS_CACHE, hasCache)
				.request()
				.delete());
	}

	public Result<Short> _getShort(String shortId, boolean hasCache) {
		return super.toJavaResult(
				target
				.path(shortId)
				.queryParam(RestShorts.HAS_CACHE, hasCache)
				.request()
				.get(), Short.class);
	}

	public Result<List<String>> _getShorts(String userId, boolean hasCache) {
		return super.toJavaResult(
				target
				.path(userId)
				.path(RestShorts.SHORTS)
				.queryParam(RestShorts.HAS_CACHE, hasCache)
				.request()
				.accept( MediaType.APPLICATION_JSON)
				.get(), new GenericType<List<String>>() {});
	}

	public Result<Void> _follow(String userId1, String userId2, boolean isFollowing, String password, boolean hasCache) {
		return super.toJavaResult(
				target
				.path(userId1)
				.path(userId2)
				.path(RestShorts.FOLLOWERS)
				.queryParam(RestShorts.PWD, password )
				.queryParam(RestShorts.HAS_CACHE, hasCache)
				.request()
				.post( Entity.entity(isFollowing, MediaType.APPLICATION_JSON)));
	}

	public Result<List<String>> _followers(String userId, String password, boolean hasCache) {
		return super.toJavaResult(
				target
				.path(userId)
				.path(RestShorts.FOLLOWERS)
				.queryParam(RestShorts.PWD, password )
				.queryParam(RestShorts.HAS_CACHE, hasCache)
				.request()
				.accept( MediaType.APPLICATION_JSON)
				.get(), new GenericType<List<String>>() {});
	}

	public Result<Void> _like(String shortId, String userId, boolean isLiked, String password, boolean hasCache) {
		return super.toJavaResult(
				target
				.path(shortId)
				.path(userId)
				.path(RestShorts.LIKES)
				.queryParam(RestShorts.PWD, password )
				.queryParam(RestShorts.HAS_CACHE, hasCache)
				.request()
				.post( Entity.entity(isLiked, MediaType.APPLICATION_JSON)));
	}

	public Result<List<String>> _likes(String shortId, String password, boolean hasCache) {
		return super.toJavaResult(
				target
				.path(shortId)
				.path(RestShorts.LIKES)
				.queryParam(RestShorts.PWD, password )
				.queryParam(RestShorts.HAS_CACHE, hasCache)
				.request()
				.accept( MediaType.APPLICATION_JSON)
				.get(), new GenericType<List<String>>() {});
	}

	public Result<List<String>> _getFeed(String userId, String password, boolean hasCache) {
		return super.toJavaResult(
				target
				.path(userId)
				.path(RestShorts.FEED)
				.queryParam(RestShorts.PWD, password )
				.queryParam(RestShorts.HAS_CACHE, hasCache)
				.request()
				.accept( MediaType.APPLICATION_JSON)
				.get(), new GenericType<List<String>>() {});
	}

	public Result<Void> _deleteAllShorts(String userId, String password, String token, boolean hasCache) {
		return super.toJavaResult(
				target
				.path(userId)
				.path(RestShorts.SHORTS)
				.queryParam(RestShorts.PWD, password )
				.queryParam(RestShorts.TOKEN, token )
				.queryParam(RestShorts.HAS_CACHE, hasCache)
				.request()
				.delete());
	}
	
	public Result<Void> _verifyBlobURI(String blobId) {
		return super.toJavaResult(
				target
				.path(blobId)
				.request()
				.get());
	}
		
	@Override
	public Result<Short> createShort(String userId, String password, boolean hasCache) {
		return super.reTry( () -> _createShort(userId, password, hasCache));
	}

	@Override
	public Result<Void> deleteShort(String shortId, String password, boolean hasCache) {
		return super.reTry( () -> _deleteShort(shortId, password, hasCache));
	}

	@Override
	public Result<Short> getShort(String shortId, boolean hasCache) {
		return super.reTry( () -> _getShort(shortId, hasCache));
	}

	@Override
	public Result<List<String>> getShorts(String userId, boolean hasCache) {
		return super.reTry( () -> _getShorts(userId, hasCache));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password, boolean hasCache) {
		return super.reTry( () -> _follow(userId1, userId2, isFollowing, password, hasCache));
	}

	@Override
	public Result<List<String>> followers(String userId, String password, boolean hasCache) {
		return super.reTry( () -> _followers(userId, password, hasCache));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password, boolean hasCache) {
		return super.reTry( () -> _like(shortId, userId, isLiked, password, hasCache));
	}

	@Override
	public Result<List<String>> likes(String shortId, String password, boolean hasCache) {
		return super.reTry( () -> _likes(shortId, password, hasCache));
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password, boolean hasCache) {
		return super.reTry( () -> _getFeed(userId, password, hasCache));
	}

	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token, boolean hasCache) {
		return super.reTry( () -> _deleteAllShorts(userId, password, token, hasCache));
	}
}
