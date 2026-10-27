package org.example.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.servlet.AuthApiServletCopy;
import org.example.util.JsonUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ApiAuthenticationFilter implements Filter {

    private record PublicRule(String method, String path) {}

    private final List<PublicRule> publicRules = new ArrayList<>();

    @Override
    public void init(FilterConfig config) throws ServletException {
        String value = config.getInitParameter("publicRequests");
        if (value == null) {
            return;                                   // nothing public
        }
        for (String line : value.split("\\R")) {      // split on any line break
            String rule = line.trim();
            if (rule.isEmpty()) {
                continue;
            }
            String[] parts = rule.split("\\s+");
            if (parts.length != 2) {
                throw new ServletException("Bad publicRequests rule: '" + rule
                        + "'. Expected: METHOD PATH");
            }
            publicRules.add(new PublicRule(parts[0].toUpperCase(), parts[1]));
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (isPublicRequest(req)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        Object userId = session == null
                ? null
                : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);

        if (!(userId instanceof String id) || id.isBlank()) {
            JsonUtil.sendError(res, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicRequest(HttpServletRequest req) {
        String path = req.getServletPath();
        String method = req.getMethod();
        for (PublicRule rule : publicRules) {
            boolean methodMatches = rule.method().equals("*") || rule.method().equalsIgnoreCase(method);
            if (methodMatches && rule.path().equals(path)) {
                return true;
            }
        }
        return false;
    }
}