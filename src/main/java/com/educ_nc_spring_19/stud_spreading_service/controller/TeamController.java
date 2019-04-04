package com.educ_nc_spring_19.stud_spreading_service.controller;

import com.educ_nc_spring_19.educ_nc_spring_19_common.common.dto.MentorDTO;
import com.educ_nc_spring_19.educ_nc_spring_19_common.common.dto.StudentDTO;
import com.educ_nc_spring_19.educ_nc_spring_19_common.common.dto.TeamDTO;
import com.educ_nc_spring_19.stud_spreading_service.mapper.TeamMapper;
import com.educ_nc_spring_19.stud_spreading_service.client.MasterDataClient;
import com.educ_nc_spring_19.stud_spreading_service.model.entity.Group;
import com.educ_nc_spring_19.stud_spreading_service.service.GroupService;
import com.educ_nc_spring_19.stud_spreading_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Log4j2
@RequiredArgsConstructor
@RestController
@RequestMapping("/stud-spreading-service/rest/api/v1/team")
public class TeamController {
    private final GroupService groupService;
    private final TeamMapper teamMapper;
    private final MasterDataClient masterDataClient;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<TeamDTO> find() {
        MentorDTO mentorDTO = masterDataClient.getMentorByUserId(userService.getCurrentUserId());
        if (mentorDTO == null) {
            log.log(Level.WARN, "find(): mentorDTO is null ");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } else {
            log.log(Level.INFO, "find(): mentorDTO = " + mentorDTO.toString());
            Optional<Group> group = groupService.findByMentorId(mentorDTO.getId());
            if (!group.isPresent()) {
                log.log(Level.WARN, "group is null");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            log.log(Level.INFO, "group = " + group.get().getId());
            log.log(Level.INFO, "groupStudents = " + group.get().getStudents());
            List<StudentDTO> students = masterDataClient.getStudentsById(group.get().getStudents());
            if (students == null) {
                log.log(Level.WARN, "students is null");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            log.log(Level.INFO, "find() should be OK");
            return ResponseEntity.status(HttpStatus.OK).body(teamMapper.toTeamDTO(group.get(), students));
        }
    }
}