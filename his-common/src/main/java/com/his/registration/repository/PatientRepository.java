package com.his.registration.repository;

import com.his.registration.model.Patient;
import com.his.shared.database.BaseRepository;
import com.his.shared.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class PatientRepository extends BaseRepository {

    public List<Patient> findAll() {
        String sql = "SELECT * FROM patients ORDER BY id DESC";
        try {
            return queryList(sql, this::mapPatient);
        } catch (Exception e) {
            throw new DatabaseException("查询所有患者失败", e);
        }
    }

    public Patient findById(int id) {
        String sql = "SELECT * FROM patients WHERE id = ?";
        try {
            return querySingle(sql, this::mapPatient, id);
        } catch (Exception e) {
            throw new DatabaseException("根据ID查询患者失败", e);
        }
    }

    public Patient findByPatientNo(String patientNo) {
        String sql = "SELECT * FROM patients WHERE patient_no = ?";
        try {
            return querySingle(sql, this::mapPatient, patientNo);
        } catch (Exception e) {
            throw new DatabaseException("根据患者编号查询失败", e);
        }
    }

    public List<Patient> search(String keyword) {
        String sql = "SELECT * FROM patients WHERE name LIKE ? OR phone LIKE ? OR id_card LIKE ? ORDER BY id DESC";
        String likeKeyword = "%" + keyword + "%";
        try {
            return queryList(sql, this::mapPatient, likeKeyword, likeKeyword, likeKeyword);
        } catch (Exception e) {
            throw new DatabaseException("搜索患者失败", e);
        }
    }

    public Patient save(Patient patient) {
        String sql = "INSERT INTO patients (patient_no, name, gender, birth_date, age, id_card, phone, address, blood_type, allergy_info, medical_insurance_type) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            int id = executeInsert(sql,
                    patient.getPatientNo(),
                    patient.getName(),
                    patient.getGender(),
                    patient.getBirthDate(),
                    patient.getAge(),
                    patient.getIdCard(),
                    patient.getPhone(),
                    patient.getAddress(),
                    patient.getBloodType(),
                    patient.getAllergyHistory(),
                    patient.getMedicalInsurance());
            patient.setId(id);
            return patient;
        } catch (Exception e) {
            throw new DatabaseException("保存患者失败", e);
        }
    }

    public void update(Patient patient) {
        String sql = "UPDATE patients SET patient_no=?, name=?, gender=?, birth_date=?, age=?, id_card=?, phone=?, address=?, blood_type=?, allergy_info=?, medical_insurance_type=? WHERE id=?";
        try {
            executeUpdate(sql,
                    patient.getPatientNo(),
                    patient.getName(),
                    patient.getGender(),
                    patient.getBirthDate(),
                    patient.getAge(),
                    patient.getIdCard(),
                    patient.getPhone(),
                    patient.getAddress(),
                    patient.getBloodType(),
                    patient.getAllergyHistory(),
                    patient.getMedicalInsurance(),
                    patient.getId());
        } catch (Exception e) {
            throw new DatabaseException("更新患者失败", e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM patients WHERE id = ?";
        try {
            executeUpdate(sql, id);
        } catch (Exception e) {
            throw new DatabaseException("删除患者失败", e);
        }
    }

    private Patient mapPatient(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.setId(rs.getInt("id"));
        p.setPatientNo(rs.getString("patient_no"));
        p.setName(rs.getString("name"));
        p.setGender(rs.getString("gender"));
        p.setBirthDate(rs.getDate("birth_date") != null ? rs.getDate("birth_date").toLocalDate() : null);
        p.setAge(rs.getInt("age"));
        p.setIdCard(rs.getString("id_card"));
        p.setPhone(rs.getString("phone"));
        p.setAddress(rs.getString("address"));
        p.setBloodType(rs.getString("blood_type"));
        p.setAllergyHistory(rs.getString("allergy_info"));
        p.setMedicalInsurance(rs.getString("medical_insurance_type"));
        p.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        p.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return p;
    }
}
