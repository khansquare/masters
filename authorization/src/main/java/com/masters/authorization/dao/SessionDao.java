package com.masters.authorization.dao;

import java.util.List;

import com.masters.authorization.model.Session;

public interface SessionDao {
	void saveOrUpdateSession(Session session);	
	Session getSession(String trace);
	Session getSession(int userId, String trace);
	List<Session> getSessions(int userId);
	void deleteSession(Session session);
	void deleteSessions(int userId);
}