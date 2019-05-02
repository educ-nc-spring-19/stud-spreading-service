package com.educ_nc_spring_19.mentoring_engine.service;

import com.educ_nc_spring_19.educ_nc_spring_19_common.common.StudentStatusBind;
import com.educ_nc_spring_19.educ_nc_spring_19_common.common.dto.MentorDTO;
import com.educ_nc_spring_19.educ_nc_spring_19_common.common.dto.StudentDTO;
import com.educ_nc_spring_19.educ_nc_spring_19_common.common.enums.StudentStatus;
import com.educ_nc_spring_19.mentoring_engine.client.MasterDataClient;
import com.educ_nc_spring_19.mentoring_engine.model.entity.Cauldron;
import com.educ_nc_spring_19.mentoring_engine.model.entity.Pool;
import com.educ_nc_spring_19.mentoring_engine.service.repo.GroupRepository;
import com.educ_nc_spring_19.mentoring_engine.model.entity.Group;
import com.educ_nc_spring_19.mentoring_engine.model.entity.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.logging.log4j.Level;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
@Service
public class GroupService {
    private final GroupRepository groupRepository;
    private final StageService stageService;
    private final MasterDataClient masterDataClient;
    private final PoolService poolService;
    private final CauldronService cauldronService;
    private final UserService userService;

    private static final UUID DISTRIBUTION_STAGE_ID = UUID.fromString("390748bf-2b6a-4b4e-93c5-51f431eae1db");
    private static final UUID FIRST_MEETING_STAGE_ID = UUID.fromString("d3674b05-f966-45cb-9216-d2a103ce139f");

    public Optional<Group> findByMentorId(UUID mentorId) {
        Optional<Group> optionalGroup = groupRepository.findByMentorId(mentorId);
        optionalGroup.ifPresent(group ->
                log.log(Level.DEBUG,"Group(id=" + group.getId() + ") found by Mentor(id=" + mentorId + ")"));
        return optionalGroup;
    }

    public Optional<Group> findById(UUID id) {
        Optional<Group> optionalGroup = groupRepository.findById(id);
        optionalGroup.ifPresent(group ->
                log.log(Level.DEBUG, "Group(id=" + group.getId() + ") found by id"));
        return optionalGroup;
    }

    public List<Group> findAll() {
        List<Group> groups = IterableUtils.toList(groupRepository.findAll());

        log.log(Level.DEBUG,
                "Groups found by findAll(): " + groups.stream().map(Group::getId).collect(Collectors.toList()));
        return groups;
    }

    public List<Group> findAllById(Iterable<UUID> ids) {
        List<Group> groups = IterableUtils.toList(groupRepository.findAllById(ids));

        log.log(Level.DEBUG,
                "Groups found by findAllById(): " + groups.stream().map(Group::getId).collect(Collectors.toList()));
        return groups;
    }

    public List<Group> findAllByStageId(UUID stageId) {
        List<Group> groups = IterableUtils.toList(groupRepository.findAllByStageId(stageId));

        log.log(Level.DEBUG,
                "Groups found by findAllById(): " + groups.stream().map(Group::getId).collect(Collectors.toList()));
        return groups;
    }

    public Group create(String name) throws IllegalArgumentException, NoSuchElementException {
        MentorDTO currentMentorDTO = this.getCurrentMentorDTO();
        UUID mentorId = currentMentorDTO.getId();

        Optional<Group> optionalGroup = findByMentorId(mentorId);
        if (optionalGroup.isPresent()) {
            throw new IllegalArgumentException("Mentor(id=" + mentorId
                    + ") already own Group(id=" + optionalGroup.get().getId() + ")");
        }

        Group group = new Group();
        group.setName(name);
        group.setMentorId(mentorId);

        //final UUID DISTRIBUTION_STAGE_ID = UUID.fromString("390748bf-2b6a-4b4e-93c5-51f431eae1db");
        Optional<Stage> stage = stageService.findById(DISTRIBUTION_STAGE_ID);
        if (stage.isPresent()) {
            group.setStage(stage.get());
            group.setStageId(DISTRIBUTION_STAGE_ID);
        } else {
            throw new IllegalArgumentException("Can't create new group cause illegal UUID in 'DISTRIBUTION_STAGE_ID': '"
                    + DISTRIBUTION_STAGE_ID.toString() + "'");
        }

        group = groupRepository.save(group);
        log.log(Level.INFO, "Group(id=" + group.getId() + ") created for Mentor(id=" + mentorId + ")");
        return group;
    }

    public void deleteById(UUID id) throws NoSuchElementException {
        Optional<Group> optionalGroup = groupRepository.findById(id);
        if (!optionalGroup.isPresent()) {
            throw new NoSuchElementException("Group(id=" + id + ") doesn't exist");
        }

        MentorDTO mentorDTO = this.getCurrentMentorDTO();
        Group group = optionalGroup.get();
        if (mentorDTO.getDirectionId() == null) {
            throw new NoSuchElementException("Mentor(id=" + mentorDTO.getId() + ") direction is 'null'");
        } else if (CollectionUtils.isNotEmpty(group.getStudents())) {
            Optional<Pool> optionalPool = poolService.findByDirectionId(mentorDTO.getDirectionId());
            if (optionalPool.isPresent()) {
                Pool pool = optionalPool.get();

                Set<UUID> studentIdsToAdd = group.getStudents().stream()
                        .map(StudentStatusBind::getId)
                        .collect(Collectors.toSet());

                pool.getStudents().addAll(studentIdsToAdd);
                pool = poolService.save(pool);

                log.log(Level.INFO, "Pool(id=" + pool.getId() + ") updated with Students: " + studentIdsToAdd);
            } else {
                throw new NoSuchElementException("Pool for Direction(id=" + mentorDTO.getDirectionId() + ") is absent");
            }
        }

        groupRepository.delete(group);
        log.log(Level.INFO, "Group(id=" + id + ") deleted");
    }

    public void deleteAll() {
        groupRepository.deleteAll();
        log.log(Level.INFO, "All Groups deleted");
    }

    private MentorDTO getCurrentMentorDTO() throws NoSuchElementException {
        UUID currentUserId = userService.getCurrentUserId();
        MentorDTO currentMentorDTO = masterDataClient.getMentorByUserId(currentUserId);

        if (currentMentorDTO == null) {
            log.log(Level.WARN, "Can't find Mentor by User(id=" + currentUserId.toString() + ")");
            throw new NoSuchElementException("Can't find Mentor by User(id=" + currentUserId.toString() + ")");
        }

        return currentMentorDTO;
    }

    // TO DO (??): Check in Master Data for backup existence
    public Group setBackupId(UUID backupId) throws IllegalArgumentException, NoSuchElementException {
        MentorDTO currentMentorDTO = this.getCurrentMentorDTO();
        // find Group for current mentor
        Optional<Group> optionalGroup = groupRepository.findByMentorId(currentMentorDTO.getId());

        if (optionalGroup.isPresent()) {
            Group group = optionalGroup.get();
            group.setBackupId(backupId);
            group = groupRepository.save(group);

            log.log(Level.INFO, "Mentor(id=" + backupId + ") was set as backup for Group(id=" + group.getId() + ")");
            return group;
        } else {
            throw new IllegalArgumentException("Group for Mentor(id=" + currentMentorDTO.getId() + ") doesn't exist");
        }
    }

    public List<Group> saveAll(Iterable<Group> groups) {
        return IterableUtils.toList(groupRepository.saveAll(groups));
    }

    public Group save(Group group) {
        return groupRepository.save(group);
    }

    public Group addStudentId(UUID studentId) throws IllegalArgumentException, NoSuchElementException {
        // get current mentor
        MentorDTO currentMentorDTO = this.getCurrentMentorDTO();
        // find Group for current mentor
        Optional<Group> optionalGroup = groupRepository.findByMentorId(currentMentorDTO.getId());

        if (optionalGroup.isPresent()) {
            Group group = optionalGroup.get();
            if (CollectionUtils.isNotEmpty(group.getStudents())
                    && group.getStudents().stream().map(StudentStatusBind::getId).anyMatch(id -> id.equals(studentId))) {
                throw new IllegalArgumentException("Student(id=" + studentId
                        + ") already in Group(id=" + group.getId() + ")");
            }

            StudentDTO studentDTO = masterDataClient.getStudentById(studentId);
            if (studentDTO == null) {
                throw new IllegalArgumentException("Can't get Student(id=" + studentId + ") from master-data service.");
            } else if (studentDTO.getDirectionId() == null) {
                throw new NoSuchElementException("Student(id=" + studentId + ") direction is null");
            } else if (!studentDTO.getDirectionId().equals(currentMentorDTO.getDirectionId())) {
                throw new IllegalArgumentException("Can't add Student(id=" + studentId
                        + ") with Direction(id=" + studentDTO.getDirectionId()
                        + ") to Group(id=" + group.getId()
                        + ") with Direction(id=" + currentMentorDTO.getDirectionId());
            }

            // delete student from pool, if it present
            poolService.findByDirectionIdAndStudentsIs(studentDTO.getDirectionId(), studentId).ifPresent(pool -> {
                pool.getStudents().remove(studentId);
                Pool savedPool = poolService.save(pool);
                log.log(Level.INFO, "Student(id=" + studentId
                        + ") removed from Pool(id=" + savedPool.getId().toString() + ")");
            });

            // delete student from current mentor cauldron, if it present
            cauldronService.findByMentorsIsAndStudentsIs(currentMentorDTO.getId(), studentId).ifPresent(cauldron -> {
                cauldron.getStudents().remove(studentId);
                Cauldron savedCauldron = cauldronService.save(cauldron);
                log.log(Level.INFO, "Student(id=" + studentId
                        + ") removed from Cauldron(id=" + savedCauldron.getId().toString() + ")");
            });

            // Add student to group with SELECTED status
            group.getStudents().add(new StudentStatusBind(studentId, StudentStatus.SELECTED));

            group = groupRepository.save(group);
            log.log(Level.INFO, "Student(id=" + studentId + ") added to Group(id=" + group.getId() + ")");
            return group;
        } else {
            throw new NoSuchElementException("Group for Mentor(id=" + currentMentorDTO.getId() + ") doesn't exist");
        }
    }

    public Group removeStudentId(UUID studentId) throws IllegalArgumentException, NoSuchElementException {
        // get current mentor
        MentorDTO currentMentorDTO = this.getCurrentMentorDTO();
        // find Group for current mentor
        Optional<Group> optionalGroup = groupRepository.findByMentorId(currentMentorDTO.getId());

        if (optionalGroup.isPresent()) {
            Group group = optionalGroup.get();

            List<StudentStatusBind> studentStatusBinds = group.getStudents();
            if (CollectionUtils.isEmpty(studentStatusBinds)
                    || studentStatusBinds.stream().map(StudentStatusBind::getId)
                            .noneMatch(id -> id.equals(studentId))) {
                throw new IllegalArgumentException("Student(id=" + studentId
                        + ") is absent in Group(id=" + group.getId() + ")");
            }

            StudentDTO studentDTO = masterDataClient.getStudentById(studentId);
            if (studentDTO == null) {
                throw new IllegalArgumentException("Can't get Student(id=" + studentId + ") from master-data service");
            } else if (studentDTO.getDirectionId() == null) {
                throw new NoSuchElementException("Student(id=" + studentId + ") direction is null");
            }

            Optional<Pool> optionalPool = poolService.findByDirectionId(studentDTO.getDirectionId());
            if (!optionalPool.isPresent()) {
                throw new NoSuchElementException("There is no Pool for Direction(id=" + studentDTO.getDirectionId() + ")");
            }

            // remove  student from group
            studentStatusBinds.remove(studentStatusBinds.stream()
                    .filter(bind -> studentId.equals(bind.getId()))
                    .findAny()
                    .orElseThrow(() ->
                            new IllegalArgumentException("Student(id=" + studentId.toString() + ") is absent in Group")
                    )
            );

            // add student to his direction pool
            Pool pool = optionalPool.get();
            pool.getStudents().add(studentId);

            // save data to DB
            group = save(group);
            pool = poolService.save(pool);

            log.log(Level.INFO, "Student(id=" + studentId
                    + ") removed from Group(id=" + group.getId()
                    + ") and added to Pool(id=" + pool.getId() + ")");

            return group;
        } else {
            throw new NoSuchElementException("Group for Mentor(id=" + currentMentorDTO.getId().toString() + ") doesn't exist");
        }
    }

    public Group setFirstMeetingStage() throws IllegalArgumentException, IllegalStateException, NoSuchElementException {
        // get current mentor
        MentorDTO currentMentorDTO = this.getCurrentMentorDTO();
        // find Group for current mentor
        Optional<Group> optionalGroup = groupRepository.findByMentorId(currentMentorDTO.getId());
        if (optionalGroup.isPresent()) {
            Group group = optionalGroup.get();
            if (!DISTRIBUTION_STAGE_ID.equals(group.getStage().getId())) {
                throw new IllegalStateException("Group(id=" + group.getId() + ") has an illegal Stage(id="
                        + group.getStage().getId() + "). Required Stage(id=" + DISTRIBUTION_STAGE_ID + ")");
            }

            Optional<Stage> optionalStage = stageService.findById(FIRST_MEETING_STAGE_ID);
            if (!optionalStage.isPresent()) {
                throw new IllegalArgumentException("Can't find Stage(id=" + FIRST_MEETING_STAGE_ID + ")");
            }

            // setting stage
            this.setStage(group, optionalStage.get());

            // saving updated group
            group = groupRepository.save(group);

            log.log(Level.INFO, "Group(id=" + group.getId() + ") stage changed to Stage(id=" + FIRST_MEETING_STAGE_ID + ")");
            return group;
        } else {
            throw new NoSuchElementException("Group for Mentor(id=" + currentMentorDTO.getId() + ") doesn't exist");
        }
    }

    public List<Group> setFirstMeetingStageBulk() throws IllegalArgumentException {
        List<Group> groups = IterableUtils.toList(groupRepository.findAllByStageId(DISTRIBUTION_STAGE_ID));
        if (CollectionUtils.isEmpty(groups)) {
            log.log(Level.INFO, "There are no groups found for Stage(id=" + DISTRIBUTION_STAGE_ID + ")");
            return Collections.emptyList();
        }

        Optional<Stage> optionalStage = stageService.findById(FIRST_MEETING_STAGE_ID);
        if (!optionalStage.isPresent()) {
            throw new IllegalArgumentException("Can't find Stage(id=" + FIRST_MEETING_STAGE_ID + ")");
        }

        // setting stage to groups
        groups.forEach(group -> this.setStage(group, optionalStage.get()));

        // saving updated groups
        groups = IterableUtils.toList(groupRepository.saveAll(groups));
        return groups;
    }

    private void setStage(Group group, Stage stage) {
        group.setStage(stage);
        group.setStageId(stage.getId());
        log.log(Level.INFO, "Set Stage(id=" + stage.getId() + ") to Group(id=" + group.getId() + ")");
    }
}
