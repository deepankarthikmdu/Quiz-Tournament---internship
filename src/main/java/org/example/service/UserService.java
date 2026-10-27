package org.example.service;

import org.example.dao.UserDao;
import org.example.model.User;
import org.example.util.PasswordUtil;

import java.sql.SQLException;
import java.util.List;

public class UserService {

    private final UserDao userDao;

    public UserService() {
        this(new UserDao());
    }

    UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public List<User> findAll() throws SQLException {
        return userDao.findAll();
    }

    public User findById(String userId) throws SQLException {
        return userDao.findById(userId);
    }

    public User create(User user) throws SQLException {
        validate(user, true);
        user.setPassword(PasswordUtil.hash(user.getPassword()));
        return userDao.create(user);
    }

    public User authenticate(String username, String password) throws SQLException {
        if (username == null || username.isBlank() || password == null) return null;

        User storedUser = userDao.findByUsernameForAuthentication(username.trim());
        if (storedUser == null) return null;

        String savedPassword = storedUser.getPassword();
        boolean authenticated = PasswordUtil.isHashed(savedPassword)
                ? PasswordUtil.verify(password, savedPassword)
                : PasswordUtil.matchesPlaintextPassword(password, savedPassword);

        if (!authenticated) return null;

        if (!PasswordUtil.isHashed(savedPassword)) {
            userDao.replacePasswordIfUnchanged(
                    storedUser.getUserId(), savedPassword, PasswordUtil.hash(password));
        }

        User safeUser = new User();
        safeUser.setUserId(storedUser.getUserId());
        safeUser.setUsername(storedUser.getUsername());
        safeUser.setName(storedUser.getName());
        return safeUser;
    }

    public User update(String userId, User user) throws SQLException {
        validate(user, false);
        if (userDao.findById(userId) == null) {
            return null;
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(PasswordUtil.hash(user.getPassword()));
        }
        if (!userDao.update(userId, user)) {
            return null;
        }
        return userDao.findById(userId);
    }

    public boolean delete(String userId) throws SQLException {
        return userDao.delete(userId);
    }

    private void validate(User user, boolean isCreate) {
        if (isCreate && (user.getUserId() == null || user.getUserId().isBlank())) {
            throw new IllegalArgumentException("userId is required");
        }
        if (isCreate && user.getUserId().trim().length() > 36) {
            throw new IllegalArgumentException("userId must be at most 36 characters");
        }
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (user.getName() == null || user.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (isCreate && (user.getPassword() == null || user.getPassword().length() < 8)) {
            throw new IllegalArgumentException("password is required and must be at least 8 characters");
        }
        if (!isCreate && user.getPassword() != null && !user.getPassword().isBlank()
                && user.getPassword().length() < 8) {
            throw new IllegalArgumentException("password must be at least 8 characters");
        }
        if (user.getPassword() != null && user.getPassword().length() > 128) {
            throw new IllegalArgumentException("password must be at most 128 characters");
        }

        if (isCreate) {
            user.setUserId(user.getUserId().trim());
        }
        user.setUsername(user.getUsername().trim());
        user.setName(user.getName().trim());
    }
}
