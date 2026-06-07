package com.his.examination.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ExaminationRequest {
    private int id;
    private String requestNo;
    private int patientId;
    private int doctorId;
    private Integer visitId;
    private Integer inpatientId;
    private String requestType;
    private int examItemId;
    private String clinicalDiagnosis;
    private String clinicalInfo;
    private String examBodyPart;
    private LocalDate requestDate;
    private boolean isUrgent;
    private String status;
    private LocalDateTime createdAt;

    // Transient fields for display
    private String patientName = "";
    private String doctorName = "";
    private String examItemName = "";
    private String category = "";

    public ExaminationRequest() {
    }

    public ExaminationRequest(int id, String requestNo, int patientId, int doctorId,
                              Integer visitId, Integer inpatientId, String requestType,
                              int examItemId, String clinicalDiagnosis, String examBodyPart,
                              LocalDate requestDate, boolean isUrgent, String status,
                              LocalDateTime createdAt) {
        this.id = id;
        this.requestNo = requestNo;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.visitId = visitId;
        this.inpatientId = inpatientId;
        this.requestType = requestType;
        this.examItemId = examItemId;
        this.clinicalDiagnosis = clinicalDiagnosis;
        this.examBodyPart = examBodyPart;
        this.requestDate = requestDate;
        this.isUrgent = isUrgent;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public void setRequestNo(String requestNo) {
        this.requestNo = requestNo;
    }

    public int getPatientId() {
        return patientId;
    }

    public void setPatientId(int patientId) {
        this.patientId = patientId;
    }

    public int getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(int doctorId) {
        this.doctorId = doctorId;
    }

    public Integer getVisitId() {
        return visitId;
    }

    public void setVisitId(Integer visitId) {
        this.visitId = visitId;
    }

    public Integer getInpatientId() {
        return inpatientId;
    }

    public void setInpatientId(Integer inpatientId) {
        this.inpatientId = inpatientId;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public int getExamItemId() {
        return examItemId;
    }

    public void setExamItemId(int examItemId) {
        this.examItemId = examItemId;
    }

    public String getClinicalDiagnosis() {
        return clinicalDiagnosis;
    }

    public void setClinicalDiagnosis(String clinicalDiagnosis) {
        this.clinicalDiagnosis = clinicalDiagnosis;
    }

    public String getClinicalInfo() {
        return clinicalInfo;
    }

    public void setClinicalInfo(String clinicalInfo) {
        this.clinicalInfo = clinicalInfo;
    }

    public String getExamBodyPart() {
        return examBodyPart;
    }

    public void setExamBodyPart(String examBodyPart) {
        this.examBodyPart = examBodyPart;
    }

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(LocalDate requestDate) {
        this.requestDate = requestDate;
    }

    public boolean isUrgent() {
        return isUrgent;
    }

    public void setUrgent(boolean urgent) {
        isUrgent = urgent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getExamItemName() {
        return examItemName;
    }

    public void setExamItemName(String examItemName) {
        this.examItemName = examItemName;
    }

    // Alias for backward compatibility
    public void setItemName(String itemName) {
        this.examItemName = itemName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
