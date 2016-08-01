package com.masters.application.controller;

import static com.masters.authorization.model.Constants.MESSAGE;
import static com.masters.authorization.model.Constants.STATUS;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.masters.application.config.MailConfiguration;
import com.masters.authorization.model.Session;
import com.masters.authorization.model.User;
import com.masters.authorization.service.RoleService;
import com.masters.authorization.service.SessionService;
import com.masters.authorization.service.UserService;
import com.masters.utilities.encryption.Base64Utils;
import com.masters.utilities.logging.Log;
import com.masters.utilities.mail.ConfirmationMailHandler;
import com.masters.utilities.session.SessionUtils;
import com.masters.utilities.validator.FieldValidator;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;


@RestController
@RequestMapping(value = "/auth")
public class AuthRestController {	

	@Autowired
	UserService userService;

	@Autowired
	RoleService roleService;
	
	@Autowired
	SessionService sessionService;
	
	@Autowired
	private HttpServletRequest httpServletRequest;
	
	private static final String ACTIVE = "1";
	private static final String INACTIVE = "2";

	//Initializing GSON
	private static Gson gson; static {
		gson = new GsonBuilder().setPrettyPrinting().create();
	}

	@RequestMapping(value = "/login", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> login(@RequestParam(required = false) HashMap<String, String> map) {
		User user = new User();
		JsonObject object = new JsonObject();
		object.addProperty(STATUS, false);	
		if (map.get("key") == null){
			object.addProperty(MESSAGE, "Email address or username is required to be logged in");
		} else if (map.get("key").trim().equals("")){
			object.addProperty(MESSAGE, "Email or username can not be empty");
		} else if (map.get("password") == null){
			object.addProperty(MESSAGE, "Password is required to be logged in");
		} else if (map.get("password").trim().equals("")){
			object.addProperty(MESSAGE, "Password can not be empty");
		} else {			
			user = userService.getUser(map.get("key"), map.get("password"));
			if (user != null && user.getUserId() > 0) {				
				try {
					String token = UUID.randomUUID().toString();
					Session session = new Session(map);
					session.setUser(user);
					session.setToken(token);					
					sessionService.insertSession(session);
					object.addProperty(STATUS, true);
					object.addProperty(MESSAGE, "User has been logged in successfully.");
					
					//object = gson.toJsonTree(user).getAsJsonObject();
					JsonObject userJson = (JsonObject) gson.toJsonTree(user);
					userJson.addProperty("token", token);
					userJson.remove("password");
					object.add("user", userJson);
					return new ResponseEntity<String>(gson.toJson(object), HttpStatus.OK);
				} catch (MySQLIntegrityConstraintViolationException e) {
					Log.e(e);
					object.addProperty(MESSAGE, "Unable to create session. Please review your login details.");
				}								
			} else {
				object.addProperty(MESSAGE, "Either email address or password is incorrect");				
			}
		}	
		return new ResponseEntity<String>(gson.toJson(object), HttpStatus.OK);
	}

	@RequestMapping(value = "/register", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> register(@RequestParam(required = false) HashMap<String, String> map) {
		JsonObject object = new JsonObject();
		object.addProperty(STATUS, false);
		User user = new User(map);
		user.setRole(roleService.getRole(map.get("role")));
		SimpleEntry<Boolean, String> result = FieldValidator.validate(user,"firstname", "lastname", 
				"role", "email", "password", "address", "city", "state", "country");
		if (!result.getKey()) {			
			object.addProperty(MESSAGE, result.getValue());
			return new ResponseEntity<String>(gson.toJson(object), HttpStatus.OK);
		} else {
			try {
				int userId = userService.insertUser(user);				
				object.addProperty(STATUS, userId > 0);
				String replica = String.format("%0" + (10 - String.valueOf(userId).length()) + "d", 0).replace("0", String.valueOf(userId) + "-").trim();
				Log.w("replica : " + replica);
				String hash = Base64Utils.encrypt(replica).trim();
				Log.w("hash : " + hash);
				String status = Base64Utils.encrypt(ACTIVE);
				Log.e("encrypted status : " + status);
				String link = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + ":" 
						+ httpServletRequest.getServerPort() + httpServletRequest.getContextPath() + "/" + "auth/status?sts=" 
				+ URLEncoder.encode(status, "UTF-8") + "&hsh=" + URLEncoder.encode(hash, "UTF-8");				
				user.setHash(hash);
				userService.updateUser(user);
				new Thread(new ConfirmationMailHandler(MailConfiguration.class, user.getEmail(),"Verify your email address", link, "templates/confirmation.html")).start();				
				object.addProperty(MESSAGE, user.getFirstname() + " " + user.getLastname() + " has been registered successfully with username " + user.getUsername());					
				return new ResponseEntity<String>(gson.toJson(object), HttpStatus.OK);
			} catch (Exception e) {
				Log.e(e);
				object.addProperty(MESSAGE, e instanceof MySQLIntegrityConstraintViolationException  ||
						e instanceof ConstraintViolationException ? "User is already registered with " + user.getEmail() 
						: "Unable to register user. Please try again");
				return new ResponseEntity<String>(gson.toJson(object), HttpStatus.OK);
			}
		}		
	}

	@RequestMapping(value = "/status", method = RequestMethod.GET)
	public ResponseEntity<Object> status(@RequestParam(required = false) String sts, @RequestParam(required = false) String hsh) {
		HttpHeaders httpHeaders = new HttpHeaders();		
		try {
			if (hsh != null && !hsh.trim().equals("")) {
				String decodedHash = URLDecoder.decode(hsh.trim(), "UTF-8").replace(" ", "+");
				Log.w("decodedHash : " + decodedHash);
				String decryptedHash = Base64Utils.decrypt(decodedHash);
				Log.w("decryptedHash : " + decryptedHash);
				String userId = decryptedHash.split("\\-")[0].trim();
				Log.w("userId : " + userId);
				User user = userService.getUser(Integer.parseInt(userId));
				if (user != null && user.getHash() != null && user.getHash().equals(hsh.trim())) {
					Log.w(sts);					
					if (sts != null && !sts.trim().equals("")) {
						String decodedStatus = URLDecoder.decode(sts.trim(), "UTF-8");
						Log.w("decoded status : " + decodedStatus);
						String decryptedStatus = Base64Utils.decrypt(decodedStatus).trim();
						Log.e("decrypted status : " + decryptedStatus);
						byte bit = Byte.parseByte(decryptedStatus);
						Log.w("status : " + bit);
						user.setStatus(bit);
						user.setHash(null);
						userService.updateUser(user);	
					}
				} else {
					Log.e("Confirmation link has been expired!");
				}		
			}
			if (httpServletRequest != null)
			httpHeaders.setLocation(new URI(httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + ":" 
					+ httpServletRequest.getServerPort() + httpServletRequest.getContextPath() + "/"));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new ResponseEntity<>(httpHeaders, HttpStatus.SEE_OTHER);		
	}
}