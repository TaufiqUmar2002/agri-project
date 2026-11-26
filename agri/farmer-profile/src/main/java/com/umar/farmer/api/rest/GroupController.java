package com.umar.farmer.api.rest;

import com.umar.farmer.service.GroupService;
import com.umar.payload.request.farmer.CreateGroupRequest;
import com.umar.payload.request.farmer.InviteGroupMembersRequest;
import com.umar.payload.response.farmer.GroupResponse;
import com.umar.payload.response.farmer.InviteGroupMembersResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/farmers/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.createGroup(idempotencyKey, request));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<InviteGroupMembersResponse> inviteMembers(
            @PathVariable("groupId") UUID groupId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InviteGroupMembersRequest request) {
        return ResponseEntity.status(HttpStatus.MULTI_STATUS)
                .body(groupService.inviteMembers(groupId, idempotencyKey, request));
    }
}
