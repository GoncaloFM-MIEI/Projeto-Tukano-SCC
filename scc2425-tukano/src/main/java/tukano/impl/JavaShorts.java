package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static utils.DB.getOne;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import org.checkerframework.checker.units.qual.A;
import utils.Tuple;
import reactor.util.function.Tuple2;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.CosmosDBLayer;
import utils.DB;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	
	private static Shorts instance;

	private static String LIKES_NAME = "likes";

	private static String FOLLOWING_NAME = "following";

	private static CosmosDBLayer cosmos;

	private static CosmosContainer shortsContainer;
	private static CosmosContainer likesContainer;
	private static CosmosContainer followingContainer;
	
	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		cosmos = CosmosDBLayer.getInstance();
		shortsContainer = cosmos.getDB().getContainer(Shorts.NAME);
		likesContainer = cosmos.getDB().getContainer(LIKES_NAME);
		followingContainer = cosmos.getDB().getContainer(FOLLOWING_NAME);
		return instance;
	}

	private JavaShorts() {

	}
	
	
	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {
			
			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId); 
			var shrt = new Short(shortId, userId, blobUrl);

			return errorOrValue(cosmos.insertOne(shrt, shortsContainer), s -> s.copyWithLikes_And_Token(0));
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
		var likes = cosmos.query(Likes.class, query, likesContainer);

		long likesCount = likes.value().isEmpty() ? 0 : likes.value().size();
		return errorOrValue( cosmos.getOne(shortId, Short.class, shortsContainer), shrt -> shrt.copyWithLikes_And_Token( likesCount));
	}

	//TODO VER COM OS STORES SE ISTO CHEGA
	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {
					Result<?> res = cosmos.deleteOne( shrt, shortsContainer);
					if(!res.isOK())
						return Result.error(NOT_FOUND);

					//var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
					var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);

					List<Likes> likes = cosmos.query( Likes.class, query, likesContainer).value();

					for(Likes l : likes){
						cosmos.deleteOne(l, likesContainer);
					}

					return JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get() );
			});	
		});
	}

	//TODO MOSTRAR AOS STORES PARA VER SE TA BOM
	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT s.id FROM Short s WHERE s.ownerId = '%s'", userId);
		List<Map> res = errorOrValue( okUser(userId), cosmos.query(Map.class, query, shortsContainer)).value();
		List<String> ids = res.stream().map(result -> result.get("id").toString()).toList();
		return Result.ok(ids);
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));
	
		
		return errorOrResult( okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			return errorOrVoid( okUser( userId2), isFollowing ? cosmos.insertOne( f , followingContainer) : cosmos.deleteOne( f , followingContainer));
		});			
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
		List<Map> res = errorOrValue( okUser(userId), cosmos.query(Map.class, query, followingContainer)).value();
		List<String> ids = res.stream().map(result -> result.get("follower").toString()).toList();
		return Result.ok(ids);
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		
		return errorOrResult( getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid( okUser( userId, password), isLiked ? cosmos.insertOne( l, likesContainer) : cosmos.deleteOne( l, likesContainer));
		});
	}

	//TODO VER ISTO COM O STOR
	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			
			var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
			List<Map> res = cosmos.query(Map.class, query, likesContainer).value();
			List<String> ids = res.stream().map(result -> result.get("userId").toString()).toList();
			return Result.ok(ids);
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

//		final var QUERY_FMT = """
//
//				UNION
//
//				ORDER BY s.timestamp DESC""";
//		final var SECOND_QUERYEXEMPLE = "SELECT s.id, s.timestamp FROM Short s, Following f WHERE f.followee = s.ownerId AND f.follower = '%s'";

		final var FIRST_QUERY = format("SELECT s.id, s.timestamp FROM Short s WHERE	s.ownerId = '%s'", userId);
		List<Map> queryRes1 = cosmos.query(Map.class, FIRST_QUERY, shortsContainer).value();

		List<Tuple<String, Long>> res1 = queryRes1.stream()
					.map(result -> new Tuple<>((String) result.get("id"), (Long) result.get("timestamp")))
					.collect(Collectors.toList());


		final var SECOND_QUERY = format("SELECT f.followee FROM Following f WHERE f.follower = '%s'", userId);
		List<Map> res2 = cosmos.query(Map.class, SECOND_QUERY, followingContainer).value();
		List<String> followees = res2.stream().map(result -> result.get("followee").toString()).toList();

		List<Tuple<String, Long>> resultTuples = new ArrayList<>();

		for (String f : followees) {
			String query = String.format("SELECT s.id, s.timestamp FROM Short s WHERE s.ownerId = '%s'", f);
			List<Map> queryResult = cosmos.query(Map.class, query, shortsContainer).value();

			List<Tuple<String, Long>> tuples = queryResult.stream()
					.map(result -> new Tuple<>((String) result.get("id"), (Long) result.get("timestamp")))
					.collect(Collectors.toList());

			resultTuples.addAll(tuples);
		}

		res1.addAll(resultTuples);

		res1.sort((t1, t2) -> Long.compare(t2.getT2(), t1.getT2()));

		List<String> result = new ArrayList<>();
		for (Tuple<String, Long> s : res1){
			result.add(s.getT1());
		}

//		List<String> ids = res.stream().map(result -> result.get("id").toString()).toList();
		return Result.ok(result);
//		return errorOrValue( okUser( userId, password), cosmos.query(String.class, format(QUERY_FMT, userId, userId), shortsContainer));
	}
		
	protected Result<User> okUser( String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}
	
	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		if( res.error() == FORBIDDEN )
			return ok();
		else
			return error( res.error() );
	}
	
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);
		try {

			//delete shorts
			var query1 = format("DELETE Short s WHERE s.ownerId = '%s'", userId);
			cosmos.query(Short.class, query1, shortsContainer);

			//delete follows
			var query2 = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
			cosmos.query(Following.class, query2, followingContainer);

			//delete likes
			var query3 = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
			cosmos.query(Likes.class, query3, likesContainer);

		} catch (Exception e) {
			return Result.error(INTERNAL_ERROR);
		}
		return ok();
	}
	
}