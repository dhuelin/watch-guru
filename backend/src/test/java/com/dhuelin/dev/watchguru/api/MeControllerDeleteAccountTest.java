package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.streaming.trakt.TraktConnectionService;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting an account, and the one thing a database cascade cannot do for it.
 *
 * <p>A Trakt token is held at Trakt as well as here. Deleting only our copy
 * strands a live credential there that nobody can revoke any more, which is
 * the failure {@code TraktConnectionService.disconnect} already orders itself
 * to avoid -- it just was not being called on this path.
 */
class MeControllerDeleteAccountTest {

    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final ApiMapper mapper = mock(ApiMapper.class);
    private final TraktConnectionService trakt = mock(TraktConnectionService.class);

    private final MeController controller = new MeController(currentUser, users, mapper, trakt);

    private final AppUser user = new AppUser("someone@example.test", "Someone");

    @Test
    @DisplayName("the Trakt token is revoked before the row holding it is deleted")
    void revokesBeforeDeleting() {
        when(currentUser.require()).thenReturn(user);

        controller.deleteAccount();

        // The order is the whole point: revoking reads the sealed token, and
        // after the delete there is nothing left to read it from.
        InOrder order = inOrder(trakt, currentUser);
        order.verify(trakt).disconnect(user);
        order.verify(currentUser).deleteCurrentUser();
    }

    @Test
    @DisplayName("Trakt being unreachable does not stop the account being deleted")
    void deletesEvenWhenRevocationFails() {
        when(currentUser.require()).thenReturn(user);
        doThrow(new IllegalStateException("trakt is down")).when(trakt).disconnect(user);

        controller.deleteAccount();

        // An unrevoked token expires on Trakt's own schedule. A delete that
        // refuses to happen because a third party is down is the worse of the
        // two, and is the one a store would fail the app for.
        verify(currentUser).deleteCurrentUser();
    }
}
