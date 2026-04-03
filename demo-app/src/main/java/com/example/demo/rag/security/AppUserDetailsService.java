package com.example.demo.rag.security;

import com.example.demo.rag.RagException;
import com.example.demo.rag.entity.UserAccount;
import com.example.demo.rag.repo.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public AppUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return toPrincipal(user);
    }

    public AuthenticatedUser loadByUserId(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new RagException("User not found: " + userId));
        return toPrincipal(user);
    }

    private AuthenticatedUser toPrincipal(UserAccount user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                user.isEnabled()
        );
    }
}
