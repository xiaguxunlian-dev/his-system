package com.his.examination.repository;

import com.his.examination.model.ExaminationReport;
import com.his.examination.model.ExaminationRequest;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ExaminationRepository extends BaseRepository {

    // ==================== ExaminationRequest ====================

    public List<ExaminationRequest> findAllRequests() {
        String sql = "SELECT er.*, p.name AS patient_name, d.name AS doctor_name, ei.item_name, ei.category " +
                     "FROM examination_requests er " +
                     "JOIN patients p ON er.patient_id = p.id " +
                     "JOIN doctors d ON er.doctor_id = d.id " +
                     "JOIN exam_items ei ON er.item_id = ei.id " +
                     "ORDER BY er.id DESC";
        try {
            return queryList(sql, this::mapRequest);
        } catch (Exception e) {
            throw new DatabaseException("查询所有检查申请失败", e);
        }
    }

    public ExaminationRequest findRequestById(int id) {
        String sql = "SELECT er.*, p.name AS patient_name, d.name AS doctor_name, ei.item_name, ei.category " +
                     "FROM examination_requests er " +
                     "JOIN patients p ON er.patient_id = p.id " +
                     "JOIN doctors d ON er.doctor_id = d.id " +
                     "JOIN exam_items ei ON er.item_id = ei.id " +
                     "WHERE er.id = ?";
        try {
            return querySingle(sql, this::mapRequest, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询检查申请失败", e);
        }
    }

    public List<ExaminationRequest> findRequestsByPatientId(int patientId) {
        String sql = "SELECT er.*, p.name AS patient_name, d.name AS doctor_name, ei.item_name, ei.category " +
                     "FROM examination_requests er " +
                     "JOIN patients p ON er.patient_id = p.id " +
                     "JOIN doctors d ON er.doctor_id = d.id " +
                     "JOIN exam_items ei ON er.item_id = ei.id " +
                     "WHERE er.patient_id = ? ORDER BY er.request_date DESC";
        try {
            return queryList(sql, this::mapRequest, patientId);
        } catch (Exception e) {
            throw new DatabaseException("根据患者ID查询检查申请失败", e);
        }
    }

    public List<ExaminationRequest> findPendingRequests() {
        String sql = "SELECT er.*, p.name AS patient_name, d.name AS doctor_name, ei.item_name, ei.category " +
                     "FROM examination_requests er " +
                     "JOIN patients p ON er.patient_id = p.id " +
                     "JOIN doctors d ON er.doctor_id = d.id " +
                     "JOIN exam_items ei ON er.item_id = ei.id " +
                     "WHERE er.status = '已申请' ORDER BY er.request_date";
        try {
            return queryList(sql, this::mapRequest);
        } catch (Exception e) {
            throw new DatabaseException("查询待处理检查申请失败", e);
        }
    }

    public ExaminationRequest saveRequest(ExaminationRequest er) {
        String sql = "INSERT INTO examination_requests (request_no, patient_id, doctor_id, visit_id, admission_id, item_id, clinical_info, exam_body_part, request_date, is_urgent, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    er.getRequestNo(),
                    er.getPatientId(),
                    er.getDoctorId(),
                    er.getVisitId(),
                    er.getInpatientId(),
                    er.getExamItemId(),
                    er.getClinicalInfo(),
                    er.getExamBodyPart(),
                    er.getRequestDate(),
                    er.isUrgent(),
                    er.getStatus());
            er.setId(id);
            return er;
        } catch (Exception e) {
            throw new DatabaseException("保存检查申请失败", e);
        }
    }

    public void updateRequest(ExaminationRequest er) {
        String sql = "UPDATE examination_requests SET request_no=?, patient_id=?, doctor_id=?, visit_id=?, admission_id=?, item_id=?, clinical_info=?, exam_body_part=?, request_date=?, is_urgent=?, status=? WHERE id=?";
        try {
            executeUpdate(sql,
                    er.getRequestNo(),
                    er.getPatientId(),
                    er.getDoctorId(),
                    er.getVisitId(),
                    er.getInpatientId(),
                    er.getExamItemId(),
                    er.getClinicalInfo(),
                    er.getExamBodyPart(),
                    er.getRequestDate(),
                    er.isUrgent(),
                    er.getStatus(),
                    er.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新检查申请失败", e);
        }
    }

    public void deleteRequest(int id) {
        String sql = "DELETE FROM examination_requests WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除检查申请失败", e);
        }
    }

    // ==================== ExaminationReport ====================

    public List<ExaminationReport> findAllReports() {
        String sql = "SELECT * FROM examination_reports ORDER BY id DESC";
        try {
            return queryList(sql, this::mapReport);
        } catch (Exception e) {
            throw new DatabaseException("查询所有检查报告失败", e);
        }
    }

    public ExaminationReport findReportById(int id) {
        String sql = "SELECT * FROM examination_reports WHERE id = ?";
        try {
            return querySingle(sql, this::mapReport, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询检查报告失败", e);
        }
    }

    public ExaminationReport findReportByRequestId(int requestId) {
        String sql = "SELECT * FROM examination_reports WHERE request_id = ?";
        try {
            return querySingle(sql, this::mapReport, requestId);
        } catch (Exception e) {
            throw new DatabaseException("根据申请ID查询检查报告失败", e);
        }
    }

    public ExaminationReport saveReport(ExaminationReport report) {
        String sql = "INSERT INTO examination_reports (request_id, report_no, findings, conclusion, is_abnormal, is_critical, tech_name, doctor_name, report_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    report.getRequestId(),
                    report.getReportNo(),
                    report.getExamFindings(),
                    report.getExamConclusion(),
                    report.isAbnormal(),
                    report.isCritical(),
                    report.getTechName(),
                    report.getDoctorName(),
                    report.getReportDate());
            report.setId(id);
            return report;
        } catch (Exception e) {
            throw new DatabaseException("保存检查报告失败", e);
        }
    }

    public void updateReport(ExaminationReport report) {
        String sql = "UPDATE examination_reports SET request_id=?, report_no=?, findings=?, conclusion=?, is_abnormal=?, is_critical=?, tech_name=?, doctor_name=?, report_date=? WHERE id=?";
        try {
            executeUpdate(sql,
                    report.getRequestId(),
                    report.getReportNo(),
                    report.getExamFindings(),
                    report.getExamConclusion(),
                    report.isAbnormal(),
                    report.isCritical(),
                    report.getTechName(),
                    report.getDoctorName(),
                    report.getReportDate(),
                    report.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新检查报告失败", e);
        }
    }

    public void deleteReport(int id) {
        String sql = "DELETE FROM examination_reports WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除检查报告失败", e);
        }
    }

    // ==================== T-8.1.4 联动：检查→门诊 ====================

    /**
     * T-8.1.4 联动：查询新出具的检查报告（用于医生工作站新报告提醒）
     * @param since 查询自此时间以来的新报告
     */
    public List<ExaminationReport> findNewReports(java.time.LocalDateTime since) {
        String sql = "SELECT er.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM examination_reports er " +
                     "LEFT JOIN patients p ON er.patient_id = p.id " +
                     "LEFT JOIN doctors d ON er.doctor_id = d.id " +
                     "WHERE er.created_at > ? AND er.status = '已审核' " +
                     "ORDER BY er.created_at DESC";
        try {
            return queryList(sql, this::mapReportWithJoin, since);
        } catch (Exception e) {
            throw new DatabaseException("查询新检查报告失败", e);
        }
    }

    /**
     * T-8.1.4 联动：根据visit_id查询已完成的检查报告（医生工作站查看患者报告）
     */
    public List<ExaminationReport> findReportsByVisitId(int visitId) {
        String sql = "SELECT er.*, p.name AS patient_name, d.name AS doctor_name " +
                     "FROM examination_reports er " +
                     "LEFT JOIN patients p ON er.patient_id = p.id " +
                     "LEFT JOIN doctors d ON er.doctor_id = d.id " +
                     "WHERE er.visit_id = ? " +
                     "ORDER BY er.report_date DESC";
        try {
            return queryList(sql, this::mapReportWithJoin, visitId);
        } catch (Exception e) {
            throw new DatabaseException("根据就诊ID查询检查报告失败", e);
        }
    }

    // ==================== RowMappers ====================

    private ExaminationReport mapReportWithJoin(ResultSet rs) throws SQLException {
        ExaminationReport report = mapReport(rs);
        try { report.setPatientName(rs.getString("patient_name")); } catch (SQLException ignore) {}
        try { report.setDoctorName(rs.getString("doctor_name")); } catch (SQLException ignore) {}
        return report;
    }

    private ExaminationRequest mapRequest(ResultSet rs) throws SQLException {
        ExaminationRequest er = new ExaminationRequest();
        er.setId(rs.getInt("id"));
        er.setRequestNo(rs.getString("request_no"));
        er.setPatientId(rs.getInt("patient_id"));
        er.setDoctorId(rs.getInt("doctor_id"));
        er.setVisitId(rs.getObject("visit_id") != null ? rs.getInt("visit_id") : null);
        er.setInpatientId(rs.getObject("admission_id") != null ? rs.getInt("admission_id") : null);
        er.setExamItemId(rs.getInt("item_id"));
        er.setClinicalInfo(rs.getString("clinical_info"));
        er.setExamBodyPart(rs.getString("exam_body_part"));
        er.setRequestDate(rs.getDate("request_date") != null ? rs.getDate("request_date").toLocalDate() : null);
        er.setUrgent(rs.getBoolean("is_urgent"));
        er.setStatus(rs.getString("status"));
        try { er.setPatientName(rs.getString("patient_name")); } catch (SQLException ignore) {}
        try { er.setDoctorName(rs.getString("doctor_name")); } catch (SQLException ignore) {}
        try { er.setItemName(rs.getString("item_name")); } catch (SQLException ignore) {}
        try { er.setCategory(rs.getString("category")); } catch (SQLException ignore) {}
        return er;
    }

    private ExaminationReport mapReport(ResultSet rs) throws SQLException {
        ExaminationReport report = new ExaminationReport();
        report.setId(rs.getInt("id"));
        report.setRequestId(rs.getInt("request_id"));
        report.setReportNo(rs.getString("report_no"));
        report.setExamFindings(rs.getString("findings"));
        report.setExamConclusion(rs.getString("conclusion"));
        report.setAbnormal(rs.getBoolean("is_abnormal"));
        report.setCritical(rs.getBoolean("is_critical"));
        report.setTechName(rs.getString("tech_name"));
        report.setDoctorName(rs.getString("doctor_name"));
        report.setReportDate(rs.getDate("report_date") != null ? rs.getDate("report_date").toLocalDate() : null);
        report.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return report;
    }
}
