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
            // 凭码加入即成员账号（共同点单/做菜；主账号可授予全权限）
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
                .map(m -> toMemberView(m, usersById.get(m.getUserId())))
                .toList();
        return new KitchenDetail(toView(kitchen, me.getRole(), null), memberViews);
    }

    /** 修改厨房信息（名称/公告）：主账号或被授予全权限的成员。 */
    @Transactional
    public KitchenView update(Long userId, Long kitchenId, String name, String announcement) {
        KitchenMember me = requireFullAccess(kitchenId, userId, "只有主账号或全权限成员可以修改厨房信息");
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

    /** 主账号解散厨房：连同成员关系一并删除。 */
    @Transactional
    public void dissolve(Long userId, Long kitchenId) {
        KitchenMember me = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        if (!me.isOwner()) {
            throw new BusinessException(403, "只有主账号可以解散厨房");
        }
        List<KitchenMember> members = memberRepository.findByKitchenIdOrderByJoinedAtAsc(kitchenId);
        memberRepository.deleteAll(members);
        kitchenRepository.deleteById(kitchenId);
    }

    // ---------- 成员管理：自定义名字/职称 + 全权限授予 ----------

    /**
     * 编辑成员的自定义名字/职称/全权限。
     * 权限：主账号可改任何成员（含全权限开关）；成员只能改自己的名字与职称。
     */
    @Transactional
    public MemberView updateMember(Long userId, Long kitchenId, Long targetUserId,
                                   String alias, String title, Integer fullAccess) {
        KitchenMember me = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        KitchenMember target = memberRepository.findByKitchenIdAndUserId(kitchenId, targetUserId)
                .orElseThrow(() -> new BusinessException(404, "成员不存在"));
        boolean selfEdit = userId.equals(targetUserId);
        if (!me.isOwner() && !selfEdit) {
            throw new BusinessException(403, "只有主账号可以编辑其他成员");
        }
        if (!selfEdit || me.isOwner()) {
            // 主账号改别人（或改自己）时可带全权限；自己不是主账号时改自己不带全权限语义
            if (fullAccess != null && me.isOwner() && !selfEdit) {
                if (target.isOwner()) {
                    throw new BusinessException("主账号本身就是全权限，无需设置");
                }
                target.setFullAccess(fullAccess == 1 ? 1 : 0);
            }
        }
        if (alias != null) {
            String trimmed = alias.trim();
            if (trimmed.length() > 20) {
                throw new BusinessException("自定义名字最多20个字");
            }
            target.setAlias(trimmed);
        }
        if (title != null) {
            String trimmed = title.trim();
            if (trimmed.length() > 10) {
                throw new BusinessException("职称最多10个字");
            }
            target.setTitle(trimmed);
        }
        memberRepository.save(target);
        return toMemberView(target, userRepository.findById(targetUserId).orElse(null));
    }

    /** 厨房管理操作（改信息等）要求主账号或全权限成员。 */
    private KitchenMember requireFullAccess(Long kitchenId, Long userId, String message) {
        KitchenMember m = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        if (!m.hasFullAccess()) {
            throw new BusinessException(403, message);
        }
        return m;
    }

    private KitchenView toView(Kitchen k, String myRole, String ownerNickname) {
        long memberCount = memberRepository.countByKitchenId(k.getId());
        User owner = userRepository.findById(k.getOwnerId()).orElse(null);
        return new KitchenView(
                k.getId(), k.getName(), k.getCode(), k.getLevel(),
                k.getAnnouncement(), memberCount, myRole,
                ownerNickname != null ? ownerNickname : (owner == null ? "" : owner.getNickname()));
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
            String announcement, long memberCount, String myRole, String ownerNickname) {}

    public record MemberView(Long userId, String nickname, String role, String joinedAt,
                             String alias, String title, Integer fullAccess) {}

    private MemberView toMemberView(KitchenMember m, User u) {
        return new MemberView(m.getUserId(),
                u == null ? "已注销用户" : u.getNickname(),
                m.getRole(), m.getJoinedAt(),
                m.getAlias() == null ? "" : m.getAlias(),
                m.getTitle() == null ? "" : m.getTitle(),
                m.getFullAccess() == null ? 0 : m.getFullAccess());
    }

    public record KitchenDetail(KitchenView kitchen, List<MemberView> members) {}
}
