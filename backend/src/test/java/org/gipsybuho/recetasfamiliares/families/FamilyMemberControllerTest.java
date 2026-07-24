package org.gipsybuho.recetasfamiliares.families;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class FamilyMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listMembersReturnsSelfAsOwner() throws Exception {
        String email = uniqueEmail("members-self");
        RegisteredUser user = register(email, "Familia Miembros");

        mockMvc.perform(get("/api/v1/families/{familyId}/members", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(email))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void listMembersReturnsForbiddenForNonMember() throws Exception {
        RegisteredUser ownerA = register(uniqueEmail("members-owner-a"), "Familia A");
        RegisteredUser userB  = register(uniqueEmail("members-user-b"), "Familia B");

        mockMvc.perform(get("/api/v1/families/{familyId}/members", ownerA.familyId())
                        .header("Authorization", "Bearer " + userB.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMembersRequiresAuthentication() throws Exception {
        RegisteredUser user = register(uniqueEmail("members-noauth"), "Familia NoAuth");

        mockMvc.perform(get("/api/v1/families/{familyId}/members", user.familyId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateOwnerRoleReturnsBadRequest() throws Exception {
        RegisteredUser user = register(uniqueEmail("members-role-owner"), "Familia RoleOwner");

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}/role",
                        user.familyId(), user.userId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeSelfReturnsBadRequest() throws Exception {
        RegisteredUser user = register(uniqueEmail("members-remove-self"), "Familia RemoveSelf");

        mockMvc.perform(delete("/api/v1/families/{familyId}/members/{userId}",
                        user.familyId(), user.userId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inviteMemberAddsUserToFamily() throws Exception {
        RegisteredUser owner  = register(uniqueEmail("invite-owner"), "Familia Invite");
        String guestEmail = uniqueEmail("invite-guest");
        RegisteredUser guest  = register(guestEmail, "Familia Guest");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        // guest now in the owner's family — members list must have 2 entries
        mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void removeMemberHappyPath() throws Exception {
        RegisteredUser owner = register(uniqueEmail("remove-happy-owner"), "Familia RemoveHappy");
        String guestEmail = uniqueEmail("remove-happy-guest");
        RegisteredUser guest = register(guestEmail, "Familia RemoveHappyGuest");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), guest.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());

        // only the owner remains
        mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void updateMemberRoleHappyPath() throws Exception {
        RegisteredUser owner = register(uniqueEmail("role-happy-owner"), "Familia RoleHappy");
        String guestEmail = uniqueEmail("role-happy-guest");
        RegisteredUser guest = register(guestEmail, "Familia RoleHappyGuest");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}/role",
                        owner.familyId(), guest.userId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void firstOwnerCanCreateAdminMemberChangeRoleAndRemoveMembers() throws Exception {
        String ownerEmail = uniqueEmail("first-owner");
        RegisteredUser owner = register(ownerEmail, "Familia FirstOwner");
        String createdAdminEmail = uniqueEmail("created-admin");
        String createdMemberEmail = uniqueEmail("created-member");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Created Admin",
                                  "password": "created-admin-password",
                                  "role": "ADMIN"
                                }
                                """.formatted(createdAdminEmail)))
                .andExpect(status().isCreated());

        MvcResult members = mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value(ownerEmail))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].email").value(createdAdminEmail))
                .andExpect(jsonPath("$[1].role").value("ADMIN"))
                .andReturn();
        String createdAdminId = readUserIdByEmail(members, createdAdminEmail);

        MvcResult adminLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "created-admin-password"
                                }
                                """.formatted(createdAdminEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.family.id").value(owner.familyId()))
                .andReturn();
        String createdAdminToken = read(adminLogin, "accessToken");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + createdAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Created Member",
                                  "password": "created-member-password",
                                  "role": "MEMBER"
                                }
                                """.formatted(createdMemberEmail)))
                .andExpect(status().isCreated());

        MvcResult afterAdminCreate = mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].email").value(createdMemberEmail))
                .andExpect(jsonPath("$[2].role").value("MEMBER"))
                .andReturn();
        String createdMemberId = readUserIdByEmail(afterAdminCreate, createdMemberEmail);

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}/role",
                        owner.familyId(), createdAdminId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "MEMBER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        mockMvc.perform(delete("/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), createdMemberId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void removeMemberRevokesRefreshToken() throws Exception {
        RegisteredUser owner = register(uniqueEmail("revoke-owner"), "Familia Revoke");
        String guestEmail = uniqueEmail("revoke-guest");
        RegisteredUser guest = register(guestEmail, "Familia RevokeGuest");

        // invite guest to owner's family
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        // expel guest — triggers bulk token revocation
        mockMvc.perform(delete("/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), guest.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());

        // guest's refresh token issued at registration must now be rejected
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(guest.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMemberDetailsWithTemporaryPasswordChangesLoginAndRevokesRefreshToken() throws Exception {
        RegisteredUser owner = register(uniqueEmail("edit-temp-owner"), "Familia EditTemp");
        String guestEmail = uniqueEmail("edit-temp-guest");
        String updatedEmail = uniqueEmail("edit-temp-updated");

        // Invita creando la cuenta directamente (sin familia propia previa) para que el
        // objetivo pertenezca a una sola familia: set-temporary-password esta bloqueado
        // para cuentas multi-familia (proteccion contra toma de cuenta cross-family).
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Invitada Original",
                                  "password": "very-secure-password",
                                  "role": "MEMBER"
                                }
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        MvcResult members = mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        String guestId = readUserIdByEmail(members, guestEmail);

        MvcResult guestLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "very-secure-password"
                                }
                                """.formatted(guestEmail)))
                .andExpect(status().isOk())
                .andReturn();
        String guestRefreshToken = read(guestLogin, "refreshToken");

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), guestId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Invitada Actualizada",
                                  "email": "%s",
                                  "passwordAction": "SET_TEMPORARY",
                                  "temporaryPassword": "temporary-password-123"
                                }
                                """.formatted(updatedEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Invitada Actualizada"))
                .andExpect(jsonPath("$.email").value(updatedEmail))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "very-secure-password"
                                }
                                """.formatted(guestEmail)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "temporary-password-123"
                                }
                                """.formatted(updatedEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(guestId))
                .andExpect(jsonPath("$.user.displayName").value("Invitada Actualizada"))
                .andExpect(jsonPath("$.user.email").value(updatedEmail));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(guestRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMemberDetailsBlocksTemporaryPasswordWhenTargetBelongsToMultipleFamilies() throws Exception {
        RegisteredUser owner = register(uniqueEmail("edit-multi-owner"), "Familia EditMulti");
        String guestEmail = uniqueEmail("edit-multi-guest");
        RegisteredUser guest = register(guestEmail, "Familia EditMultiGuest");

        // El guest ya tiene su propia familia (arriba); al invitarlo a otra familia mas
        // queda con 2 membresias activas, exactamente el escenario que debe bloquearse.
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), guest.userId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Invitada Actualizada",
                                  "email": "%s",
                                  "passwordAction": "SET_TEMPORARY",
                                  "temporaryPassword": "temporary-password-123"
                                }
                                """.formatted(guestEmail)))
                .andExpect(status().isBadRequest());

        // login con la password original sigue funcionando: nada cambio
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "very-secure-password"
                                }
                                """.formatted(guestEmail)))
                .andExpect(status().isOk());
    }

    @Test
    void updateMemberDetailsRejectsMemberSelfOwnerAndDuplicateEmail() throws Exception {
        String ownerEmail = uniqueEmail("edit-rules-owner");
        RegisteredUser owner = register(ownerEmail, "Familia EditRules");
        String adminEmail = uniqueEmail("edit-rules-admin");
        RegisteredUser admin = register(adminEmail, "Familia EditRulesAdmin");
        String usedEmail = uniqueEmail("edit-rules-used");
        register(usedEmail, "Familia EditRulesUsed");
        String memberEmail = uniqueEmail("edit-rules-member");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "ADMIN"}
                                """.formatted(adminEmail)))
                .andExpect(status().isCreated());

        // Miembro invitado creando la cuenta directamente (sin familia propia previa),
        // para que quede en una sola familia y el cambio de email no choque con el
        // bloqueo de multi-familia; este test cubre self/OWNER/email-duplicado, no eso.
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Test User",
                                  "password": "very-secure-password",
                                  "role": "MEMBER"
                                }
                                """.formatted(memberEmail)))
                .andExpect(status().isCreated());

        MvcResult members = mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        String memberId = readUserIdByEmail(members, memberEmail);

        MvcResult memberLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "very-secure-password"
                                }
                                """.formatted(memberEmail)))
                .andExpect(status().isOk())
                .andReturn();
        String memberAccessToken = read(memberLogin, "accessToken");

        String body = """
                {
                  "displayName": "Nombre Editado",
                  "email": "%s",
                  "passwordAction": "NONE"
                }
                """.formatted(memberEmail);

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), memberId)
                        .header("Authorization", "Bearer " + memberAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), owner.userId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Owner Editado",
                                  "email": "%s",
                                  "passwordAction": "NONE"
                                }
                                """.formatted(ownerEmail)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), owner.userId())
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Owner Editado",
                                  "email": "%s",
                                  "passwordAction": "NONE"
                                }
                                """.formatted(ownerEmail)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), memberId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Nombre Editado",
                                  "email": "%s",
                                  "passwordAction": "NONE"
                                }
                                """.formatted(usedEmail)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateMemberDetailsResetEmailFailsClosedWhenMailIsDisabled() throws Exception {
        RegisteredUser owner = register(uniqueEmail("edit-reset-owner"), "Familia EditReset");
        String memberEmail = uniqueEmail("edit-reset-member");
        RegisteredUser member = register(memberEmail, "Familia EditResetMember");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(memberEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), member.userId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Test User",
                                  "email": "%s",
                                  "passwordAction": "SEND_RESET"
                                }
                                """.formatted(memberEmail)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void inviteDuplicateMemberReturns201Silently() throws Exception {
        RegisteredUser owner = register(uniqueEmail("invite-dup-owner"), "Familia InviteDup");
        String guestEmail = uniqueEmail("invite-dup-guest");
        register(guestEmail, "Familia Guest2");

        // first invite — adds guest to family
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        // second invite — same user, same family: anti-enumeration, must be 201 silent no-op
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        // member list must still be exactly 2 (no duplicate row created)
        mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void inviteNonExistentUserReturns201Silently() throws Exception {
        RegisteredUser owner = register(uniqueEmail("invite-notfound-owner"), "Familia NotFound");

        // Anti-enumeration: unregistered email must not reveal itself via 404
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(uniqueEmail("nobody"))))
                .andExpect(status().isCreated());

        // family membership must remain unchanged (only owner)
        mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void inviteRequiresAdminRole() throws Exception {
        RegisteredUser owner  = register(uniqueEmail("invite-perm-owner"), "Familia InvPerm");
        String memberEmail = uniqueEmail("invite-perm-member");
        RegisteredUser member = register(memberEmail, "Familia InvPerm2");
        String targetEmail = uniqueEmail("invite-perm-target");
        register(targetEmail, "Familia InvPerm3");

        // owner invites member as MEMBER
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(memberEmail)))
                .andExpect(status().isCreated());

        // member (not admin) tries to invite — must be forbidden
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(targetEmail)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record RegisteredUser(String accessToken, String refreshToken, String familyId, String userId) {}

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Test User",
                                  "password": "very-secure-password",
                                  "familyName": "%s"
                                }
                                """.formatted(email, familyName)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new RegisteredUser(
                response.get("accessToken").asText(),
                response.get("refreshToken").asText(),
                response.get("family").get("id").asText(),
                response.get("user").get("id").asText()
        );
    }

    private String read(MvcResult result, String field) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get(field).asText();
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private String readUserIdByEmail(MvcResult result, String email) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode member : response) {
            if (email.equals(member.get("email").asText())) {
                return member.get("userId").asText();
            }
        }
        throw new AssertionError("Member not found: " + email);
    }
}
