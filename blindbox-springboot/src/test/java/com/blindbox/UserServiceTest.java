package com.blindbox;
import com.blindbox.core.*;
import com.blindbox.core.Models.*;
import com.blindbox.security.JwtService;
import com.blindbox.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    @Test void bcryptByteLimitIsValidatedBeforeDatabaseAccess() {
        PlatformMapper db=mock(PlatformMapper.class);
        UserService service=new UserService(db,mock(PasswordEncoder.class),mock(JwtService.class),false);
        assertThrows(BusinessException.class,()->service.register(new Credentials("collector","密".repeat(30))));
        verifyNoInteractions(db);
    }
    @Test void productionRechargeIsDisabled() {
        PlatformMapper db=mock(PlatformMapper.class);
        UserService service=new UserService(db,mock(PasswordEncoder.class),mock(JwtService.class),false);
        assertThrows(BusinessException.class,()->service.recharge(1,100,"request-001"));verifyNoInteractions(db);
    }
    @Test void replayDoesNotCreditAgainAndConflictingAmountRejects() {
        PlatformMapper db=mock(PlatformMapper.class);User user=new User();user.setBalance(100L);
        Wallet wallet=new Wallet();wallet.setAmount(100L);
        when(db.lockUser(1)).thenReturn(user);when(db.rechargeByRequest(1,"request-001")).thenReturn(wallet);
        UserService service=new UserService(db,mock(PasswordEncoder.class),mock(JwtService.class),true);
        assertEquals(false,service.recharge(1,100,"request-001").get("credited"));
        var sequence=inOrder(db);sequence.verify(db).lockUser(1);sequence.verify(db).rechargeByRequest(1,"request-001");
        assertThrows(BusinessException.class,()->service.recharge(1,200,"request-001"));
        verify(db,never()).credit(anyInt(),anyLong());
    }
    @Test void atomicCreditAndLedgerShareOperation() {
        PlatformMapper db=mock(PlatformMapper.class);User user=new User();user.setBalance(100L);
        when(db.lockUser(1)).thenReturn(user);when(db.credit(1,50)).thenReturn(1);
        when(db.wallet(1,"request-001","RECHARGE",50,150,null)).thenReturn(1);
        UserService service=new UserService(db,mock(PasswordEncoder.class),mock(JwtService.class),true);
        assertEquals(150L,service.recharge(1,50,"request-001").get("balance"));
    }
}
