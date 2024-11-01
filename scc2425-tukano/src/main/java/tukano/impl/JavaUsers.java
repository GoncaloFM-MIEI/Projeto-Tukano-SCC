package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.azure.cosmos.CosmosContainer;
import com.fasterxml.jackson.annotation.JsonAlias;
import redis.clients.jedis.Jedis;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import tukano.impl.data.UserDAO;
import utils.DB;
import utils.CosmosDBLayer;
import utils.JSON;
import utils.RedisCache;

public class JavaUsers implements Users {
	
	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;

	private static CosmosDBLayer cosmos;

	private static CosmosContainer usersContainer;
	
	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();

		return instance;
	}
	
	private JavaUsers() {
		cosmos = CosmosDBLayer.getInstance();
		usersContainer = cosmos.getDB().getContainer(Users.NAME);
	}



	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if( badUserInfo( user ) ) {
			return error(BAD_REQUEST);
		}

		Locale.setDefault(Locale.US);
		Result<String> res = errorOrValue(cosmos.insertOne(user, usersContainer), user.getUserId());
		if (res.isOK()){
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {

				var key = "users:" + user.getUserId();
				var value = JSON.encode(user);
				jedis.set(key, value);
				int expirationTime = 240;
				jedis.expire(key, expirationTime);
			}
		}
		//return errorOrValue(cosmos.insertOne(user, usersContainer), user.getUserId());

		return res;

	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			var key = "user:" + userId;
			var user = JSON.decode(jedis.get(key), User.class);
			if (user != null)
				return Result.ok(user);

			Result<User> u = validatedUserOrError(cosmos.getOne(userId, User.class, usersContainer), pwd);

			if (u.isOK()) {
				var value = JSON.encode(u);
				jedis.set(key, value);
				int expirationTime = 240;
				jedis.expire(key, expirationTime);
			}

			return u;
		}
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);



		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			var key = "users:" + userId;
			var value = jedis.get(key);
			var user = JSON.decode(value, User.class);
			if (user != null) {
				Result<User> res = errorOrValue(cosmos.updateOne(user.updateFrom(other), usersContainer), user);
				var newValue = JSON.encode(res.value());
				jedis.set(key, newValue);
				return res;
			}else {

				Result<User> res = errorOrResult(validatedUserOrError(cosmos.getOne(userId, User.class, usersContainer), pwd), newUser -> cosmos.updateOne(newUser.updateFrom(other), usersContainer));

				if (res.isOK()) {
					var key1 = "users:" + res.value().getUserId();
					var value1 = JSON.encode(res.value());
					jedis.set(key1, value1);
					int expirationTime = 240;
					jedis.expire(key, expirationTime);

				}
				return res;
			}
		}

	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null )
			return error(BAD_REQUEST);

		Result<User> res = cosmos.getOne(userId, User.class, usersContainer);

		//TODO ESCLARECER A SITUACÂO DO RETURN
		return errorOrResult( validatedUserOrError(res, pwd), user -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread( () -> {
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
			}).start();

			//Result<User> res = cosmos.getOne(userId, User.class);
			cosmos.deleteOne(user, usersContainer);

			try (Jedis jedis = RedisCache.getCachePool().getResource()) {

				var key = "users:" + res.value().getUserId();
				jedis.del(key);
			}

			//	cosmos.getOne( userId, User.class);

			return res;
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		//TODO TEMOS MESMO QUE TER O .TOUPPSERCASE() no patter? Não funciona com ele :)
		var query = format("SELECT * FROM users u WHERE u.id LIKE '%%%s%%'", pattern);
		var hits = cosmos.query(User.class, query, usersContainer);
				//.stream()
				//.map(User::copyWithoutPassword)
				//.toList();

		return ok(hits.value().stream().toList());
	}

	
	private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res;
	}
	
	private boolean badUserInfo( User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}
	
	private boolean badUpdateUserInfo( String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
	}
}
