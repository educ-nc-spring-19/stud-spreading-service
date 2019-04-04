package com.educ_nc_spring_19.stud_spreading_service.service.repo;

import com.educ_nc_spring_19.stud_spreading_service.model.entity.Group;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends CrudRepository<Group, UUID> {
    Optional<Group> findByMentorId(UUID mentorId);
}
