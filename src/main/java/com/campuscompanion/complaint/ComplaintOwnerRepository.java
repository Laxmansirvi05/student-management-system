package com.campuscompanion.complaint;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintOwnerRepository extends JpaRepository<ComplaintOwner, Long> {

  List<ComplaintOwner> findByComplaintIdIn(Collection<Long> complaintIds);
}
