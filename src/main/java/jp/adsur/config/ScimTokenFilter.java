//package jp.adsur.config;
//
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//
//public class ScimTokenFilter extends OncePerRequestFilter {
//
//    private final String scimToken;
//
//    public ScimTokenFilter(String scimToken) {
//        this.scimToken = scimToken;
//    }
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain filterChain)
//            throws ServletException, IOException {
//        String authHeader = request.getHeader("Authorization");
//
//        if (authHeader == null || !authHeader.equals("Bearer " + scimToken)) {
//            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid SCIM token");
//            return;
//        }
//
//        filterChain.doFilter(request, response);
//    }
//}
