package org.akaza.openclinica.controller;

import core.org.akaza.openclinica.bean.extract.ArchivedDatasetFileBean;
import core.org.akaza.openclinica.bean.login.UserAccountBean;
import core.org.akaza.openclinica.dao.extract.ArchivedDatasetFileDAO;
import core.org.akaza.openclinica.dao.hibernate.StudyDao;
import core.org.akaza.openclinica.domain.datamap.Study;
import core.org.akaza.openclinica.service.UtilService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.http.entity.ContentType;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


@Controller
@RequestMapping(value = "/auth/api")
@Api(value = "ScheduledExtractJob", tags = {"Scheduled Extract Job"}, description = "REST API for Scheduled Extract Job")
public class ScheduledExtractController {

    private final static Logger logger = LoggerFactory.getLogger(ScheduledJobController.class);

    @Autowired
    private UtilService utilService;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private StudyDao studyDao;

    @Autowired
    private ArchivedDatasetFileDAO archivedDatasetFileDAO;


    @ApiOperation(value = "To get latest scheduled extract dataset ids and creation time for the job name at study level", notes = "only work for authorized users with the right access permission")
    @RequestMapping(value = "/studies/{studyOID}/extractJobs/{jobName}/jobExecutions", method = RequestMethod.GET)
    public @ResponseBody
    ResponseEntity<Object> getScheduledExtractJobDatasetIdsAndCreationTime(@PathVariable("studyOID") String studyOid,
                                                                           @PathVariable("jobName") String jobName,
                                                                           HttpServletRequest request) throws SchedulerException {

        Study study = studyDao.findByOcOID(studyOid.trim());
        if (study == null) {
            return new ResponseEntity<>("Invalid studyOid.", HttpStatus.NOT_FOUND);
        }

        UserAccountBean userAccountBean = utilService.getUserAccountFromRequest(request);
        if (!userAccountBean.isSysAdmin() && !userAccountBean.isTechAdmin()) {
            return new ResponseEntity<>("User must be type admin.", HttpStatus.UNAUTHORIZED);
        }

        utilService.setSchemaFromStudyOid(studyOid);
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(jobName, "XsltTriggersExportJobs"));
        if (jobDetail == null) {
            return new ResponseEntity<>("Invalid job name.", HttpStatus.NOT_FOUND);
        }
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
        String jobUuid = jobDataMap.getString("job_uuid");
        if (jobUuid == null) {
            return new ResponseEntity<>("Could not find extract jobs.", HttpStatus.NO_CONTENT);
        }

        logger.debug("Found job uuid: " + jobUuid);

        ArrayList<ArchivedDatasetFileBean> extracts = archivedDatasetFileDAO.findByJobUuid(jobUuid);
        String output = "";
        for (ArchivedDatasetFileBean adfb : extracts) {
            output += " Dataset Id: " + adfb.getDatasetFileUuid() + "  Date Created: " + adfb.getDateCreated() + "\n";
        }

        return new ResponseEntity<>("Extract files for job name " + jobName + ": \n" + output, HttpStatus.OK);
    }


    @ApiOperation(value = "To get latest scheduled extract dataset ids and creation time for the job name at study level", notes = "only work for authorized users with the right access permission")
    @RequestMapping(value = "/studies/extractJobs/{fileUuid}/jobExecutions", method = RequestMethod.GET, produces = "application/zip")
    public @ResponseBody
    ResponseEntity<byte[]> getScheduledExtract(@PathVariable("fileUuid") String fileUuid,
                                                          HttpServletRequest request,
                                                          HttpServletResponse response) throws IOException{

        UserAccountBean userAccountBean = utilService.getUserAccountFromRequest(request);
        if (!userAccountBean.isSysAdmin() && !userAccountBean.isTechAdmin()) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        ArchivedDatasetFileBean extract = (ArchivedDatasetFileBean) archivedDatasetFileDAO.findByDatasetFileUuid(fileUuid);
        if (extract == null) {
            logger.debug("Archived Dataset File not found.");
        }
        String filePath = extract.getFileReference();
        logger.debug("Found location of file: " + filePath);


        File file = new File(filePath);
        byte[] contents = java.nio.file.Files.readAllBytes(file.toPath());

        response.setContentType(ContentType.APPLICATION_OCTET_STREAM.toString());
        response.setContentLength((int) file.length());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"" + file.getName() + "\""));
        response.setHeader(HttpHeaders.CONTENT_LENGTH, ""+file.length());

        return new ResponseEntity<>(contents, HttpStatus.OK);

    }

}
