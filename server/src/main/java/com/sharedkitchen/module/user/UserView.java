package com.sharedkitchen.module.user;

/** 返回给客户端的用户视图：手机号打码，不暴露敏感字段。 */
public record UserView(
        Long id,
        String nickname,
        String avatar,
        String openId,
        String phoneMasked,
        String createdAt
) {
    public static UserView of(User u) {
        String phone = u.getPhone();
        String masked = phone.length() == 11
                ? phone.substring(0, 3) + "****" + phone.substring(7)
                : phone;
        return new UserView(u.getId(), u.getNickname(), u.getAvatar(), u.getOpenId(),
                masked, u.getCreatedAt());
    }
}
