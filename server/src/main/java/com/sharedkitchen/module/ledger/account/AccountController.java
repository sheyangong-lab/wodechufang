package com.sharedkitchen.module.ledger.account;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 账本账户（三钱包）与资产总览。 */
@RestController
@RequestMapping("/api/kitchens/{kitchenId}/ledger")
@Validated
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/accounts")
    public ApiResponse<List<AccountService.AccountView>> list(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(accountService.list(userId(request), kitchenId));
    }

    public record AccountReq(@NotBlank String name, @NotBlank String type,
                             Long initialFen, Long creditLimitFen) {}

    @PostMapping("/accounts")
    public ApiResponse<AccountService.AccountView> create(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated AccountReq req) {
        return ApiResponse.ok(accountService.create(userId(request), kitchenId,
                req.name(), req.type(), req.initialFen(), req.creditLimitFen()));
    }

    public record AccountUpdateReq(String name, Integer sort, Long initialFen, Long creditLimitFen) {}

    @PutMapping("/accounts/{aid}")
    public ApiResponse<AccountService.AccountView> update(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long aid,
            @RequestBody @Validated AccountUpdateReq req) {
        return ApiResponse.ok(accountService.update(userId(request), kitchenId, aid,
                req.name(), req.sort(), req.initialFen(), req.creditLimitFen()));
    }

    @DeleteMapping("/accounts/{aid}")
    public ApiResponse<Void> delete(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long aid) {
        accountService.delete(userId(request), kitchenId, aid);
        return ApiResponse.ok();
    }

    @GetMapping("/assets")
    public ApiResponse<AccountService.AssetsView> assets(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(accountService.assets(userId(request), kitchenId));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
    }
}
