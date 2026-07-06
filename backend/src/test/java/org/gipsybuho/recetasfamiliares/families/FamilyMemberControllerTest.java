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
        RegisteredUser user = register("members-self@example.com", "Familia Miembros");

        mockMvc.perform(get("/api/v1/families/{familyId}/members", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("members-self@example.com"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void listMembersReturnsForbiddenForNonMember() throws Exception {
        RegisteredUser ownerA = register("members-owner-a@example.com", "Familia A");
        RegisteredUser userB  = register("members-user-b@example.com", "Familia B");

        mockMvc.perform(get("/api/v1/families/{familyId}/members", ownerA.familyId())
                        .header("Authorization", "Bearer " + userB.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMembersRequiresAuthentication() throws Exception {
        RegisteredUser user = register("members-noauth@example.com", "Familia NoAuth");

        mockMvc.perform(get("/api/v1/families/{familyId}/members", user.familyId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateOwnerRoleReturnsBadRequest() throws Exception {
        RegisteredUser user = register("members-role-owner@example.com", "Familia RoleOwner");

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
        RegisteredUser user = register("members-remove-self@example.com", "Familia RemoveSelf");

        mockMvc.perform(delete("/api/v1/families/{familyId}/members/{userId}",
                        user.familyId(), user.userId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inviteMemberAddsUserToFamily() throws Exception {
        RegisteredUser owner  = register("invite-owner@example.com", "Familia Invite");
        RegisteredUser guest  = register("invite-guest@example.com", "Familia Guest");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "invite-guest@example.com", "role": "MEMBER"}
                                """))
                .andExpect(status().isCreated());

        // guest now in the owner's family — members list must have 2 entries
        mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void removeMemberHappyPath() throws Exception {
        RegisteredUser owner = register("remove-happy-owner@example.com", "Familia RemoveHappy");
        RegisteredUser guest = register("remove-happy-guest@example.com", "Familia RemoveHappyGuest");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "remove-happy-guest@example.com", "role": "MEMBER"}
                                """))
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
        RegisteredUser owner = register("role-happy-owner@example.com", "Familia RoleHappy");
        RegisteredUser guest = register("role-happy-guest@example.com", "Familia RoleHappyGuest");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "role-happy-guest@example.com", "role": "MEMBER"}
                                """))
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
        RegisteredUser owner = register("first-owner@example.com", "Familia FirstOwner");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "created-admin@example.com",
                                  "displayName": "Created Admin",
                                  "password": "created-admin-password",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult members = mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("first-owner@example.com"))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].email").value("created-admin@example.com"))
                .andExpect(jsonPath("$[1].role").value("ADMIN"))
                .andReturn();
        String createdAdminId = readUserIdByEmail(members, "created-admin@example.com");

        MvcResult adminLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "created-admin@example.com",
                                  "password": "created-admin-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.family.id").value(owner.familyId()))
                .andReturn();
        String createdAdminToken = read(adminLogin, "accessToken");

        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + createdAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "created-member@example.com",
                                  "displayName": "Created Member",
                                  "password": "created-member-password",
                                  "role": "MEMBER"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult afterAdminCreate = mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].email").value("created-member@example.com"))
                .andExpect(jsonPath("$[2].role").value("MEMBER"))
                .andReturn();
        String createdMemberId = readUserIdByEmail(afterAdminCreate, "created-member@example.com");

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
        RegisteredUser owner = register("revoke-owner@example.com", "Familia Revoke");
        RegisteredUser guest = register("revoke-guest@example.com", "Familia RevokeGuest");

        // invite guest to owner's family
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "revoke-guest@example.com", "role": "MEMBER"}
                                """))
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
    void inviteDuplicateMemberReturns201Silently() throws Exception {
        RegisteredUser owner = register("invite-dup-owner@example.com", "Familia InviteDup");
        register("invite-dup-guest@example.com", "Familia Guest2");

        // first invite — adds guest to family
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "invite-dup-guest@example.com", "role": "MEMBER"}
                                """))
                .andExpect(status().isCreated());

        // second invite — same user, same family: anti-enumeration, must be 201 silent no-op
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "invite-dup-guest@example.com", "role": "MEMBER"}
                                """))
                .andExpect(status().isCreated());

        // member list must still be exactly 2 (no duplicate row created)
        mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void inviteNonExistentUserReturns201Silently() throws Exception {
        RegisteredUser owner = register("invite-notfound-owner@example.com", "Familia NotFound");

        // Anti-enumeration: unregistered email must not reveal itself via 404
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@example.com", "role": "MEMBER"}
                                """))
                .andExpect(status().isCreated());

        // family membership must remain unchanged (only owner)
        mockMvc.perform(get("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void inviteRequiresAdminRole() throws Exception {
        RegisteredUser owner  = register("invite-perm-owner@example.com", "Familia InvPerm");
        RegisteredUser member = register("invite-perm-member@example.com", "Familia InvPerm2");
        register("invite-perm-target@example.com", "Familia InvPerm3");

        // owner invites member as MEMBER
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "invite-perm-member@example.com", "role": "MEMBER"}
                                """))
                .andExpect(status().isCreated());

        // member (not admin) tries to invite — must be forbidden
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "invite-perm-target@example.com", "role": "MEMBER"}
                                """))
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
