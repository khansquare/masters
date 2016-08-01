package com.masters.authorization.dao;

import java.util.List;

import com.masters.authorization.model.Session;
import com.masters.authorization.model.User;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;

public interface SessionDao {
	int insertSession(Session session) throws MySQLIntegrityConstraintViolationException;
	void deleteSession(Session session);
	//Session getSession(String userId);
	List<Session> getSessions(int userId);
}
