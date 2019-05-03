package com.educ_nc_spring_19.mentoring_engine.controller;

import com.educ_nc_spring_19.mentoring_engine.enums.InviteState;
import com.educ_nc_spring_19.mentoring_engine.mapper.CauldronMapper;
import com.educ_nc_spring_19.mentoring_engine.mapper.GroupMapper;
import com.educ_nc_spring_19.mentoring_engine.mapper.PoolMapper;
import com.educ_nc_spring_19.mentoring_engine.model.entity.Cauldron;
import com.educ_nc_spring_19.mentoring_engine.model.entity.Group;
import com.educ_nc_spring_19.mentoring_engine.model.entity.Pool;
import com.educ_nc_spring_19.mentoring_engine.service.InviteService;
import com.educ_nc_spring_19.mentoring_engine.service.WorkflowService;
import com.educ_nc_spring_19.mentoring_engine.util.InviteLinkPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.Level;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Log4j2
@RequiredArgsConstructor
@RestController
@RequestMapping("/mentoring-engine/rest/api/v1/rpc")
public class RemoteProcedureController {
    private final InviteService inviteService;
    private final WorkflowService workflowService;

    private final CauldronMapper cauldronMapper;
    private final GroupMapper groupMapper;
    private final ObjectMapper objectMapper;
    private final PoolMapper poolMapper;

    @SuppressWarnings("unchecked")
    @PostMapping(path = "/workflow-init", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity workflowInit() {
        Map<String, List<?>> resultOfInit = workflowService.init();
        if (MapUtils.isEmpty(resultOfInit)) {
            log.log(Level.WARN, "result of workflow init is empty");
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }

        Map<String, List<?>> response = new HashMap<>();

        // Unchecked casts, but we trust to map keys returned from WorkflowService
        if (CollectionUtils.isNotEmpty(resultOfInit.get("pools"))) {
            response.put("pools", poolMapper.toPoolsDTO((List<Pool>) resultOfInit.get("pools")));
        }

        if (CollectionUtils.isNotEmpty(resultOfInit.get("cauldrons"))) {
            response.put("cauldrons", cauldronMapper.toCauldronsDTO((List<Cauldron>) resultOfInit.get("cauldrons")));
        }

        if (CollectionUtils.isNotEmpty(resultOfInit.get("groups"))) {
            response.put("groups", groupMapper.toGroupsDTO((List<Group>) resultOfInit.get("groups")));
        }

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @SuppressWarnings("unchecked")
    @GetMapping(path = "/first-meeting", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity setGroupFirstMeeting() {

        Map<String, Object> response = new LinkedHashMap<>();
        try {
            Map<String, Object> resultOfStageChange = workflowService.setFirstMeetingGroupStageAndGetInviteLinks();

            if (resultOfStageChange.get("group") != null) {
                response.put("group", groupMapper.toGroupDTO((Group) resultOfStageChange.get("group")));
            }

            if (MapUtils.isNotEmpty((Map<UUID, InviteLinkPair>) resultOfStageChange.get("links"))) {
                response.put("links", resultOfStageChange.get("links"));
            }

        } catch (IllegalArgumentException iAE) {
            log.log(Level.WARN, iAE);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iAE);
        } catch (IllegalStateException iSE) {
            log.log(Level.WARN, iSE);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(iSE);
        } catch (NoSuchElementException nSEE) {
            log.log(Level.WARN, nSEE);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(nSEE);
        }

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping(path = "/invite", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity processInvite(@RequestParam(value = "link") String link) {
        log.log(Level.INFO, "link: '" + link + "'");

        InviteState responseState;
        try {
            responseState = inviteService.processInviteLink(link);
        } catch (IllegalArgumentException iAE) {
            log.log(Level.WARN, iAE);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iAE);
        } catch (IllegalStateException iSE) {
            log.log(Level.WARN, iSE);
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(iSE);
        } catch (NoSuchElementException nSEE) {
            log.log(Level.WARN, nSEE);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(nSEE);
        } catch (Exception e) {
            log.log(Level.WARN, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e);
        }

        String responseMessage;
        switch (responseState) {
            case ACCEPT:
                log.info("InviteState is '" + responseState + "'");
                responseMessage = "You have successfully accepted the invitation";
                break;
            case REJECT:
                log.info("InviteState is '" + responseState + "'");
                responseMessage = "You have successfully rejected the invitation";
                break;
            default:
                Exception exception = new Exception("Illegal InviteState '" + responseState + "'");
                log.log(Level.WARN, exception);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception);
        }

        return ResponseEntity.status(HttpStatus.OK).body(objectMapper.createObjectNode().put("message", responseMessage));
    }
}
