package com.sharedkitchen.module.kitchen;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.user.User;
import com.sharedkitchen.module.user.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KitchenService {

    private static final String CODE_CHARS = "0123456789abcdefghijkmnpqrstuvwxyz";
    private static final int CODE_LEN = 24;

    private final KitchenRepository kitchenRepository;
    private final KitchenMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final Random random = new SecureRandom();

    public KitchenService(KitchenRepository kitchenRepository,
                          KitchenMemberRepository memberRepository,
                          UserRepository userRepository) {
        this.kitchenRepository = kitchenRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public KitchenView create(Long userId, String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("请输入厨房名称");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 20) {
            throw new BusinessException("厨房名称最多20个字");
        }
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));

        Kitchen kitchen = new Kitchen();
        kitchen.setName(trimmed);
        kitchen.setCode(generateCode());
        kitchen.setOwnerId(userId);
        kitchen.setCreatedAt(Instant.now().toString());
        kitchenRepository.save(kitchen);

        KitchenMember member = new KitchenMember();
        member.setKitchenId(kitchen.getId());
        member.setUserId(userId);
        member.setRole(KitchenMember.ROLE_OWNER);
        member.setJoinedAt(Instant.now().toString());
        memberRepository.save(member);

        return toView(kitchen, KitchenMember.ROLE_OWNER, owner.getNickname());
    }

    @Transactional
    public KitchenView join(Long userId, String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("请输入厨房码");
        }
        Kitchen kitchen = kitchenRepository.findByCode(code.trim())
                .orElseThrow(() -> new BusinessException("厨房码不存在，请核对后重试"));
        if (kitchen.getStatus() != 1) {
            throw new BusinessException(403, "该厨房已被封禁");
        }
        KitchenMember existing = memberRepository
                .findByKitchenIdAndUserId(kitchen.getId(), userId).orElse(null);
        if (existing == null) {
            KitchenMember member = new KitchenMember();
            member.setKitchenId(kitchen.getId());
            member.setUserId(userId);
            member.setRole(KitchenMember.ROLE_MEMBER);
            member.setJoinedAt(Instant.now().toString());
            memberRepository.save(member);
            existing = member;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        return toView(kitchen, existing.getRole(), user.getNickname());
    }

    /** 我加入过的所有厨房（切换厨房的数据源）。 */
    public List<KitchenView> myKitchens(Long userId) {
        return memberRepository.findByUserIdOrderByJoinedAtAsc(userId).stream()
                .map(m -> {
                    Kitchen k = kitchenRepository.findById(m.getKitchenId()).orElse(null);
                    if (k == null) return null;
                    User user = userRepository.findById(userId).orElse(null);
                    return toView(k, m.getRole(), user == null ? "" : user.getNickname());
                })
                .toList();
    }

    /** 厨房详情 + 成员列表；仅成员可见。 */
    public KitchenDetail detail(Long userId, Long kitchenId) {
        KitchenMember me = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new BusinessException(404, "厨房不存在"));

        List<KitchenMember> members = memberRepository.findByKitchenIdOrderByJoinedAtAsc(kitchenId);
        List<Long> userIds = members.stream().map(KitchenMember::getUserId).toList();
        var usersById = userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        List<MemberView> memberViews = members.stream()
                .map(m -> {
                    User u = usersById.get(m.getUserId());
                    return new MemberView(
                            m.getUserId(),
                            u == null ? "已注销用户" : u.getNickname(),
                            m.getRole(),
                            m.getJoinedAt());
                })
                .toList();
        return new KitchenDetail(toView(kitchen, me.getRole(), null), memberViews);
    }

    /** 店长修改厨房信息（名称/公告）。 */
    @Transactional
    public KitchenView update(Long userId, Long kitchenId, String name, String announcement) {
        KitchenMember me = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        if (!KitchenMember.ROLE_OWNER.equals(me.getRole())) {
            throw new BusinessException(403, "只有店长可以修改厨房信息");
        }
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new BusinessException(404, "厨房不存在"));
        if (name != null && !name.isBlank()) {
            String trimmed = name.trim();
            if (trimmed.length() > 20) {
                throw new BusinessException("厨房名称最多20个字");
            }
            kitchen.setName(trimmed);
        }
        if (announcement != null) {
            if (announcement.length() > 100) {
                throw new BusinessException("公告最多100个字");
            }
            kitchen.setAnnouncement(announcement.trim());
        }
        kitchenRepository.save(kitchen);
        return toView(kitchen, me.getRole(), null);
    }

    /** 店长解散厨房：连同成员关系一并删除。 */
    @Transactional
    public void dissolve(Long userId, Long kitchenId) {
        KitchenMember me = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        if (!KitchenMember.ROLE_OWNER.equals(me.getRole())) {
            throw new BusinessException(403, "只有店长可以解散厨房");
        }
        List<KitchenMember> members = memberRepository.findByKitchenIdOrderByJoinedAtAsc(kitchenId);
        memberRepository.deleteAll(members);
        kitchenRepository.deleteById(kitchenId);
    }

    private KitchenView toView(Kitchen k, String myRole, String ownerNickname) {
        long memberCount = memberRepository.countByKitchenId(k.getId());
        User owner = userRepository.findById(k.getOwnerId()).orElse(null);
        return new KitchenView(
                k.getId(), k.getName(), k.getCode(), k.getLevel(),
                k.getAnnouncement(), memberCount, myRole,
                ownerNickname != null ? ownerNickname : (owner == null ? "" : owner.getNickname()),
                k.getDishQuota(), k.getCategoryQuota(), k.getVipExpireAt());
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        do {
            sb.setLength(0);
            for (int i = 0; i < CODE_LEN; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
        } while (kitchenRepository.findByCode(sb.toString()).isPresent());
        return sb.toString();
    }

    public record KitchenView(
            Long id, String name, String code, Integer level,
            String announcement, long memberCount, String myRole, String ownerNickname,
            Integer dishQuota, Integer categoryQuota, String vipExpireAt) {}

    public record MemberView(Long userId, String nickname, String role, String joinedAt) {}

    public record KitchenDetail(KitchenView kitchen, List<MemberView> members) {}
}
