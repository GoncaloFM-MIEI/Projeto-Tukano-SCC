package tukano.impl;

import com.azure.cosmos.CosmosContainer;
import redis.clients.jedis.Jedis;
import tukano.api.Short;
import tukano.api.*;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import utils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.*;

public class JavaShorts implements Shorts {

	private final String SHORT_PREFIX = "short:";
	private final String COUNTER_PREFIX = "counter:";
	private final String SHORT_LIKES_LIST_PREFIX = "shortLikes:";
	private final String USER_SHORTS_LIST_PREFIX = "userShorts:";
	private final String FEED_PREFIX = "feed:";
	private final String FOLLOWERS_PREFIX = "followers:";

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	
	private static Shorts instance;

	private static String LIKES_NAME = "likes";

	private static String FOLLOWING_NAME = "following";

	private static final boolean isPostgree = true;

	private static CosmosDBLayer cosmos;

	private static CosmosContainer shortsContainer;
	private static CosmosContainer likesContainer;
	private static CosmosContainer followingContainer;
	
	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		if(!isPostgree){
			cosmos = CosmosDBLayer.getInstance();
			shortsContainer = cosmos.getDB().getContainer(Shorts.NAME);
			likesContainer = cosmos.getDB().getContainer(LIKES_NAME);
			followingContainer = cosmos.getDB().getContainer(FOLLOWING_NAME);
		}

		return instance;
	}

	private JavaShorts() {

	}
	
	
	@Override
	public Result<Short> createShort(String userId, String password, boolean hasCache) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		//TODO verificar se é este o url correto
		String url = "http:127.0.0.1:8080/tukano/rest";
		return errorOrResult( okUser(userId, password, hasCache), user -> {

			var shortId = format("%s+%s", userId, UUID.randomUUID());
			//var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var blobUrl = format("%s/%s/%s", url, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			Result<Short> res;
			if(isPostgree){
				res = errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
			}else{
				res =  errorOrValue(cosmos.insertOne(shrt, shortsContainer), s -> {
					Log.info(()-> format("Inserted Short: %s", s));
					return s.copyWithLikes_And_Token(0);
				});
			}




			if(res.isOK() && hasCache) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					//Log.info(() -> format("\n\nSHORT CRIADO NA CACHE %s\n\n", shortId));

					var key = SHORT_PREFIX + res.value().getShortId();
					var value = JSON.encode(res.value());
					jedis.setex(key,120, value);

					var sKey = USER_SHORTS_LIST_PREFIX + userId;
					var list = jedis.lrange(sKey, 0, -1);
					if(!list.isEmpty()){
						//Log.info(() -> format("\n\nLISTA DE SHORTS DO USER NÃO ESTA VAZIA %s\n\n", shortId));
						jedis.lpush(sKey, JSON.encode(res.value().getShortId()));
						jedis.expire(sKey, 120);
					}else{
						List<Map> userShorts;

						if(isPostgree){
							var query = format("SELECT s.id FROM short s WHERE s.ownerId = '%s'", userId);
							userShorts =  DB.sql( query, Map.class);
						}else{
							var query = format("SELECT s.id FROM Short s WHERE s.ownerId = '%s'", userId);
							userShorts =  cosmos.query(Map.class, query, shortsContainer).value();
						}

						List<String> ids = new ArrayList<>();

						if (userShorts.isEmpty()) {
							ids.add(JSON.encode(res.value().getShortId()));
						}else{
							ids = userShorts.stream()
									.map(result -> result.get("id").toString())
									.toList();
						}

						jedis.rpush(sKey, ids.stream().map(JSON::encode).toArray(String[]::new));
						jedis.expire(sKey, 120);
					}
				}
			}

			return res;
		});
	}

	@Override
	public Result<Short> getShort(String shortId, boolean hasCache) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		if(hasCache){
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				//Pipeline pipeline = jedis.pipelined();
				Long  likesCount;
				var lKey = COUNTER_PREFIX + shortId;
				var key = SHORT_PREFIX + shortId;
				//var likes = jedis.get(lKey);

				List<String> values = jedis.mget(key, lKey);
				String value = values.get(0);
				String likes = values.get(1);

			/*
			Checking existence of likes in cache
			- If likes counter exist then get and renew expire time
			- else get from likes from DB and set value in cache
			*/
				if(likes != null) {
					likesCount = JSON.decode(likes, Long.class);
					//Log.info(() -> format("\n\nCOUNTER DE LIKES EXISTIA %d\n\n", likesCount));
					//jedis.expire(lKey, 120);
				}else {
					//Log.info(() -> format("\n\nCOUNTER DE LIKES NÃO EXISTIA %s\n\n", shortId));
					List<Likes> likesOnDB;
					if(isPostgree){
						var query = format("SELECT * FROM likes l WHERE l.shortId = '%s'", shortId);
						likesOnDB = DB.sql(query, Likes.class);
					}else{
						var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
						likesOnDB = cosmos.query(Likes.class, query, likesContainer).value();
					}

					//if(likesOnDB.isOK()) {
					likesCount = likesOnDB.isEmpty() ? 0 : (long) likesOnDB.size();
					jedis.setex(lKey,120, String.valueOf(likesCount));
						//pipeline.expire(lKey, 120);
					//} else {
					//	likesCount = 0L;
					//}
				}

			/*
			Checking short existence in cache
			- if exists then extend expire time and return
			with the likes count retrieved earlier
			 */

				//var value = jedis.get(key);

				if(value != null) {
					//Log.info(() -> format("\n\nGET SHORT DA CACHE %s\n\n", shortId));
					//var shortToGet = JSON.decode(value, Short.class);
					//jedis.expire(key, 120);

					//var lKey = "counter:" + shortId;
					//var likes = jedis.get(lKey);

					Short shortToGetWithLikes = JSON.decode(value, Short.class).copyWithLikes_And_Token(likesCount);
					return ok(shortToGetWithLikes);
				}

				if(isPostgree){
					return errorOrValue( DB.getOne(shortId, Short.class), shrt ->{
						var val = JSON.encode(shrt);
						jedis.setex(key,120, val);
						return shrt.copyWithLikes_And_Token(likesCount);
					});
				}else{
					return errorOrResult( cosmos.getOne(shortId, Short.class, shortsContainer), shrt -> {
						//Log.info(() -> format("\n\nGET SHORT NA DB %s\n\n", shortId));
						var val = JSON.encode(shrt);
						jedis.setex(key,120, val);
						//pipeline.expire(key,120);
						//pipeline.sync();
						Short shortToGetWithLikes = shrt.copyWithLikes_And_Token(likesCount);
						return ok(shortToGetWithLikes);
					});
				}
			}
		}else{

			if(isPostgree){
				var query = format("SELECT count(*) FROM likes l WHERE l.shortId = '%s'", shortId);
				var likes = DB.sql(query, Long.class);
				return errorOrValue( DB.getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
			}else{
				var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
				var likes = cosmos.query(Likes.class, query, likesContainer);
				long likesCount = likes.value().isEmpty() ? 0 : likes.value().size();

				return errorOrValue( cosmos.getOne(shortId, Short.class, shortsContainer), shrt -> shrt.copyWithLikes_And_Token(likesCount));
			}

		}


	}


	@Override
	public Result<Void> deleteShort(String shortId, String password, boolean hasCache) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		if(isPostgree){
			return errorOrResult( getShort(shortId, hasCache), shrt -> {

				return errorOrResult( okUser( shrt.getOwnerId(), password, hasCache), user -> {
					return DB.transaction( hibernate -> {

						hibernate.remove( shrt);

						var query = format("DELETE FROM likes l WHERE l.shortId = '%s'", shortId);
						hibernate.createNativeQuery( query, Likes.class).executeUpdate();

						if(hasCache){
							try (Jedis jedis = RedisCache.getCachePool().getResource()) {
								//Pipeline pipeline = jedis.pipelined();

								var key = SHORT_PREFIX + shortId; //short
								jedis.del(key);

								var cKey = COUNTER_PREFIX + shortId; //counter likes
								jedis.del(cKey);

								var lKey = SHORT_LIKES_LIST_PREFIX + shortId; //list likes (user ids)
								jedis.del(lKey);

								var uKey = USER_SHORTS_LIST_PREFIX + user.getUserId();
								jedis.lrem(uKey, 0, shortId);

								//pipeline.sync();
								//Set<String> fKeys = jedis.keys(FEED_PREFIX);
								//fKeys.forEach(fKey -> jedis.lrem(fKey, 0, shortId));
							}
						}

						JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get() );
					});
				});
			});
		}else{
			return errorOrResult( getShort(shortId, hasCache), shrt -> {
				return errorOrResult( okUser( shrt.getOwnerId(), password, hasCache), user -> {
					Result<?> res = cosmos.deleteOne( shrt, shortsContainer);
					if(!res.isOK())
						return Result.error(NOT_FOUND);

					//var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
					var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);

					List<Likes> likes = cosmos.query( Likes.class, query, likesContainer).value();

					for(Likes l : likes){
						cosmos.deleteOne(l, likesContainer);
					}
					if(hasCache){
						try (Jedis jedis = RedisCache.getCachePool().getResource()) {
							//Pipeline pipeline = jedis.pipelined();

							var key = SHORT_PREFIX + shortId; //short
							jedis.del(key);

							var cKey = COUNTER_PREFIX + shortId; //counter likes
							jedis.del(cKey);

							var lKey = SHORT_LIKES_LIST_PREFIX + shortId; //list likes (user ids)
							jedis.del(lKey);

							var uKey = USER_SHORTS_LIST_PREFIX + user.getUserId();
							jedis.lrem(uKey, 0, shortId);

							//pipeline.sync();
							//Set<String> fKeys = jedis.keys(FEED_PREFIX);
							//fKeys.forEach(fKey -> jedis.lrem(fKey, 0, shortId));
						}
					}
					return JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get(shrt.getBlobUrl()) );
				});
			});
		}
	}

	//TODO MOSTRAR AOS STORES PARA VER SE TA BOM
	@Override
	public Result<List<String>> getShorts(String userId, boolean hasCache) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		if(hasCache){
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = USER_SHORTS_LIST_PREFIX + userId;
				var value = jedis.lrange(key, 0, -1);

				if(!value.isEmpty()){
					//Log.info(() -> format("\n\nGET SHORTS: USER SHORTS EXISTEM NA CACHE %s\n\n", userId));
					List<String> res = value.stream().map(shrt -> JSON.decode(shrt, String.class)).toList();
//					for (var shrt: value){
//						var shortObj = JSON.decode(shrt, String.class);
//						res.add(shortObj);
//					}
					//jedis.expire(key,120);
					return ok(res);
				}

				//Log.info(() -> format("\n\nGET SHORTS: USER SHORTS NÃO EXISTEM NA CACHE %s\n\n", userId));
				List<String> ids;
				if(isPostgree){
					var query = format("SELECT s.id FROM short s WHERE s.ownerId = '%s'", userId);
					ids = errorOrValue( okUser(userId, hasCache), DB.sql( query, String.class)).value();
					jedis.rpush(key, ids.stream().map(JSON::encode).toArray(String[]::new));
				}else{
					var query = format("SELECT s.id FROM Short s WHERE s.ownerId = '%s'", userId);
					List<Map> res = errorOrValue( okUser(userId, hasCache), cosmos.query(Map.class, query, shortsContainer)).value();
					ids = res.stream().map(result -> result.get("id").toString()).toList();
					jedis.rpush(key, ids.stream().map(JSON::encode).toArray(String[]::new));
				}

//				for(String id : ids){
//					jedis.lpush(key, JSON.encode(id));
//				}
				//jedis.expire(key,120);
				return Result.ok(ids);
			}
		}else{
			if(isPostgree){
				var query = format("SELECT s.shortId FROM short s WHERE s.ownerId = '%s'", userId);
				return errorOrValue( okUser(userId, hasCache), DB.sql( query, String.class));
			}else{
				var query = format("SELECT s.id FROM Short s WHERE s.ownerId = '%s'", userId);
				List<Map> res = errorOrValue( okUser(userId, hasCache), cosmos.query(Map.class, query, shortsContainer)).value();
				List<String> ids = res.stream().map(result -> result.get("id").toString()).toList();
				return Result.ok(ids);
			}
		}

	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password, boolean hasCache) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));

		Result<Void> res = null;
		if(isPostgree){
			res = errorOrResult( okUser(userId1, password, hasCache), user -> {
				var f = new Following(userId1, userId2);
				return errorOrVoid( okUser( userId2, hasCache), isFollowing ? DB.insertOne( f ) : DB.deleteOne( f ));
			});
		}else{
			res = errorOrResult( okUser(userId1, password, hasCache), user -> {
				var f = new Following(userId1, userId2);
				return errorOrVoid( okUser( userId2, hasCache), isFollowing ? cosmos.insertOne( f , followingContainer) : cosmos.deleteOne( f , followingContainer));
			});
		}
		
		if(res.isOK() && hasCache){
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				//Pipeline pipe = jedis.pipelined();

				var key = FOLLOWERS_PREFIX + userId2;
				List<String> followersList = jedis.lrange(key, 0, -1);
				if(!followersList.isEmpty()){
					Log.info(() -> format("\n\nFOLLOW: LISTA DE FOLLOWERS EXISTE %s\n\n", userId1));
					if (isFollowing) {
						jedis.lpush(key, JSON.encode(userId1));
						jedis.expire(key,120);
					}else{
						jedis.lrem(key,1, JSON.encode(userId1));
					}
				}else {

					List<String> fol;
					if(isPostgree){
						Log.info(() -> format("\n\nFOLLOW: LISTA DE FOLLOWERS NÃO EXISTE %s\n\n", userId1));
						var query = format("SELECT f.follower FROM following f WHERE f.followee = '%s'", userId2);
						fol = errorOrValue( okUser(userId2, password, hasCache), DB.sql(query, String.class)).value();
						Log.info(() -> format("\n\nFOLLOW: LISTA DE FOLLOWERS NÃO EXISTE %s\n\n", fol));
					}else{
						var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId2);
						List<Map> followers = errorOrValue( okUser(userId2, hasCache), cosmos.query(Map.class, query, followingContainer)).value();
						fol = followers.stream().map(result -> result.get("follower").toString()).toList();
					}


					List<String> ids = new ArrayList<>();
					if (fol.isEmpty()) {
						ids.add(JSON.encode(userId1));
					}else{
						ids = fol;
					}
					jedis.rpush(key, ids.stream().map(JSON::encode).toArray(String[]::new));
					jedis.expire(key,120);
				}
				//pipe.sync();
			}
		}

		return res;
	}

	@Override
	public Result<List<String>> followers(String userId, String password, boolean hasCache) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		if(hasCache) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = FOLLOWERS_PREFIX + userId;
				List<String> followersList = jedis.lrange(key, 0, -1);
				if (!followersList.isEmpty()) {
					//Log.info(() -> format("\n\nFOLLOWERS: LISTA DE FOLLOWERS EXISTE NA CACHE %s\n\n", userId));
					//List<String> res = new ArrayList<>();
					//List<String> res = followersList.stream()
					//		.map(f -> JSON.decode(f, String.class))
					//		.toList();
					//for (String f : followersList) {
					//	res.add(JSON.decode(f, String.class));
					//}
					//jedis.expire(key,120);
					return ok(followersList.stream()
							.map(f -> JSON.decode(f, String.class))
							.toList());
				}
				//Log.info(() -> format("\n\nFOLLOWERS: LISTA DE FOLLOWERS NÃO EXISTE NA CACHE %s\n\n", userId));
				List<String> ids;
				if(isPostgree){
					var query = format("SELECT f.follower FROM following f WHERE f.followee = '%s'", userId);
					 ids = errorOrValue( okUser(userId, password, hasCache), DB.sql(query, String.class)).value();
				}else{
					var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
					List<Map> res = errorOrValue(okUser(userId, hasCache), cosmos.query(Map.class, query, followingContainer)).value();
					ids = res.stream().map(result -> result.get("follower").toString()).toList();
				}
				//for (String id : ids) {
				//	jedis.lpush(key, JSON.encode(id));
				//}
				jedis.rpush(key, ids.stream().map(JSON::encode).toArray(String[]::new));
				jedis.expire(key, 120);
				return Result.ok(ids);
			}
		}else {
			if(isPostgree){
				var query = format("SELECT f.follower FROM following f WHERE f.followee = '%s'", userId);
				return errorOrValue( okUser(userId, password, hasCache), DB.sql(query, String.class));
			}else{
				var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
				List<Map> res = errorOrValue( okUser(userId, hasCache), cosmos.query(Map.class, query, followingContainer)).value();
				List<String> ids = res.stream().map(result -> result.get("follower").toString()).toList();
				return Result.ok(ids);
			}


		}
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password, boolean hasCache) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		Result<Void> res;
		if(isPostgree){
			res =  errorOrResult( getShort(shortId, hasCache), shrt -> {
				var l = new Likes(userId, shortId, shrt.getOwnerId());
				return errorOrVoid( okUser( userId, password, hasCache), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));
			});
		}else{
			res = errorOrResult( getShort(shortId, hasCache), shrt -> {
				var l = new Likes(userId, shortId, shrt.getOwnerId());
				return errorOrVoid( okUser( userId, password, hasCache), isLiked ? cosmos.insertOne( l, likesContainer) : cosmos.deleteOne( l, likesContainer));
			});
		}


		if(res.isOK() && hasCache){
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				//Pipeline pipe = jedis.pipelined();

				var key = SHORT_LIKES_LIST_PREFIX + shortId;
				List<String> likesList = jedis.lrange(key, 0, -1);
				var lKey = COUNTER_PREFIX + shortId;
				var value = jedis.get(lKey);

				if(!likesList.isEmpty()) {
					//Log.info(() -> format("\n\nLIKE: LISTA DE LIKES EXISTE NA CACHE %s\n\n", shortId));
					if (isLiked) {
						jedis.lpush(key, JSON.encode(userId));
					}else{
						jedis.lrem(key,1, JSON.encode(userId));
					}
				}else{
					List<String> like;
					if(isPostgree){
						var query = format("SELECT l.userId FROM likes l WHERE l.shortId = '%s'", shortId);
						like =  DB.sql(query, String.class);
					}else{
						var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
						List<Map> likes = cosmos.query(Map.class, query, likesContainer).value();
						like = likes.stream().map(result -> result.get("userId").toString()).toList();
					}

					List<String> ids = new ArrayList<>();
					if (like.isEmpty()) {
						ids.add(JSON.encode(userId));
					}else{
						ids = like;
					}
					jedis.rpush(key, ids.stream().map(JSON::encode).toArray(String[]::new));
					jedis.expire(key,120);

					if(value == null){
						long likesCount = like.isEmpty() ? 0 : like.size();
						jedis.setex(lKey,120, String.valueOf(likesCount));
					}
				}

				if(value != null){
					jedis.incr(lKey);
					//Log.info(() -> format("\n\nLIKE: COUNTER DE LIKES EXISTE NA CACHE %d\n\n", newVal));
				}else {

					List<String> like;
					if(isPostgree){
						var query = format("SELECT l.userId FROM likes l WHERE l.shortId = '%s'", shortId);
						like =  DB.sql(query, String.class);
					}else{
						var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
						List<Map> likes = cosmos.query(Map.class, query, likesContainer).value();
						like = likes.stream().map(result -> result.get("userId").toString()).toList();
					}

					//var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
					//List<Map> likes = cosmos.query(Map.class, query, likesContainer).value();

					long likesCount = like.isEmpty() ? 0 : like.size();
					jedis.setex(lKey,120, String.valueOf(likesCount));
				}

				//pipe.sync();
			}
		}

		return res;
	}

	//TODO VER ISTO COM O STOR
	@Override
	public Result<List<String>> likes(String shortId, String password, boolean hasCache) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		if(hasCache) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				//Pipeline pipe = jedis.pipelined();
				var key = SHORT_LIKES_LIST_PREFIX + shortId;
				var likesList = jedis.lrange(key, 0, -1);
				if (!likesList.isEmpty()) {
					//Log.info(() -> format("\n\nLIKES: LISTA DE LIKES EXISTE NA CACHE %s\n\n", shortId));
					List<String> res = new ArrayList<>();
					for (String f : likesList) {
						res.add(JSON.decode(f, String.class));
					}
					//jedis.expire(key,120);
					return ok(res);

				}
				//Log.info(() -> format("\n\nLIKES: LISTA DE LIKES NÃO EXISTE NA CACHE %s\n\n", shortId));
				return errorOrResult(getShort(shortId, hasCache), shrt -> {

					List<String> ids;
					if(isPostgree){
						var query = format("SELECT l.userId FROM likes l WHERE l.shortId = '%s'", shortId);
						ids =  DB.sql(query, String.class);
					}else{
						var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
						List<Map> likes = cosmos.query(Map.class, query, likesContainer).value();
						ids = likes.stream().map(result -> result.get("userId").toString()).toList();
					}

					for (String id : ids) {
						jedis.lpush(key, JSON.encode(id));
					}
					//pipe.expire(key, 120);
					//pipe.sync();
					return Result.ok(ids);
				});
			}
		}else{

			if(isPostgree){
				return errorOrResult( getShort(shortId, hasCache), shrt -> {

					var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

					return errorOrValue( okUser( shrt.getOwnerId(), password , hasCache), DB.sql(query, String.class));
				});
			}else {
				return errorOrResult(getShort(shortId, hasCache), shrt -> {
					var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
					List<Map> res = cosmos.query(Map.class, query, likesContainer).value();
					List<String> ids = res.stream().map(result -> result.get("userId").toString()).toList();
					return Result.ok(ids);
				});
			}
		}
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password, boolean hasCache) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		if(isPostgree){
			final var QUERY_FMT = """
				SELECT s.id, s.timestamp 
				FROM Short s 
				WHERE s.ownerId = '%s'                
				UNION            
				SELECT s2.id, s2.timestamp 
				FROM Short s2 
				JOIN Following f ON f.followee = s2.ownerId 
				WHERE f.follower = '%s' 
				ORDER BY timestamp DESC""";

			return errorOrValue( okUser( userId, password, hasCache), DB.sql( format(QUERY_FMT, userId, userId), String.class));
		}else{
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
	}
		
	protected Result<User> okUser( String userId, String pwd, boolean hasCache) {
		return JavaUsers.getInstance().getUser(userId, pwd, hasCache); //TODO: hardcoded
	}
	
	private Result<Void> okUser( String userId , boolean hasCache) {
		var res = okUser( userId, "", hasCache);
		//Log.info(()->String.format("\n\nERROR OK USER: %s\n\n", res.error().toString()));
		if( res.error() == FORBIDDEN )
			return ok();
		else
			return error( res.error() );
	}
	
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token, boolean hasCache) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);
		try {

			//delete shorts
			//var query1 = format("DELETE Short s WHERE s.ownerId = '%s'", userId);
			var query1 = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
			List<Short> shorts = cosmos.query(Short.class, query1, shortsContainer).value();
			for(Short s : shorts){
				cosmos.deleteOne(s, shortsContainer);
				if(hasCache){
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						var key = SHORT_LIKES_LIST_PREFIX + s.getShortId();
						jedis.del(key);
						var sKey = SHORT_PREFIX + s.getShortId();
						jedis.del(sKey);
						var cKey = COUNTER_PREFIX + s.getShortId();
						jedis.del(cKey);
					}
				}
			}

			//delete follows
			//var query2 = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
			var query2 = format("SELECT * FROM Following f WHERE f.follower = '%s' OR f.followe = '%s'",userId, userId);
			List<Following> following = cosmos.query(Following.class, query2, followingContainer).value();
			for(Following f : following){
				cosmos.deleteOne(f, followingContainer);
			}

			//delete likes
			//var query3 = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
			var query3 = format("SELECT * FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
			List<Likes> likes = cosmos.query(Likes.class, query3, likesContainer).value();
			for(Likes l: likes){
				cosmos.deleteOne(l,likesContainer );
			}

			if(hasCache) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					var sKey = USER_SHORTS_LIST_PREFIX + userId;
					jedis.del(sKey);
					var fKey = FOLLOWERS_PREFIX + userId;
					jedis.del(fKey);
				}
			}


		} catch (Exception e) {
			return Result.error(INTERNAL_ERROR);
		}
		return ok();
	}
	
}