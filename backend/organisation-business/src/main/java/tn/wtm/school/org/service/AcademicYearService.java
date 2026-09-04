package tn.wtm.school.org.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.org.dto.request.SchoolYearRequest;
import tn.wtm.school.org.dto.response.SchoolYearResponse;

import java.util.List;

public interface AcademicYearService {

    SchoolYearResponse createAcademicYear(SchoolYearRequest request);

    SchoolYearResponse getAcademicYearById(Long id);

    Page<SchoolYearResponse> getAcademicYears(Pageable pageable);

    List<SchoolYearResponse> getAcademicYearsByTenant();

    SchoolYearResponse updateAcademicYear(Long id, SchoolYearRequest request);

    SchoolYearResponse toggleAcademicYearStatus(Long id, Boolean active);

    void deleteAcademicYear(Long id);
}
