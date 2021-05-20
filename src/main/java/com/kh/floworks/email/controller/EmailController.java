package com.kh.floworks.email.controller;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kh.floworks.common.Exception.NoMemberException;
import com.kh.floworks.common.utils.EmailUtils;
import com.kh.floworks.common.utils.FileUtils;
import com.kh.floworks.email.model.service.EmailService;
import com.kh.floworks.email.model.vo.Email;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/email")
@Slf4j
public class EmailController {

	private final String directory = "/resources/upload/email";

	@Autowired
	private ServletContext servletContext;

	@Autowired
	private EmailService emailService;

	@Autowired
	private ResourceLoader resourceLoader;
	
	@Autowired 
	private JavaMailSenderImpl mailSender;

	@RequestMapping("/list")
	public String emailList() {
		return "/email/emailList";
	}

	@GetMapping("/compose")
	public String emailCompose() {
		return "/email/emailCompose";
	}

	@GetMapping("/sent")
	public String emailSentList(@RequestParam String id, Model model) {

		List<Email> emailList = emailService.selectSentList(id);

		model.addAttribute("emailList", emailList);
		model.addAttribute("listType", "sent");

		return "/email/emailList";
	}

	@GetMapping("/inbox")
	public String emailIndoxList(@RequestParam String id, Model model) {

		List<Email> emailList = emailService.selectInboxList(id);

		model.addAttribute("emailList", emailList);
		model.addAttribute("listType", "indox");

		return "/email/emailList";
	}

	@RequestMapping("/drafts")
	public String emailDraftList(@RequestParam String id, Model model) {

		List<Email> emailList = emailService.selectDraftList(id);

		model.addAttribute("emailList", emailList);
		model.addAttribute("listType", "drafts");

		return "/email/emailList";
	}

	/**
	 * 
	 * @param emailNo
	 * @param model
	 * @param listType : 이메일 리스트의 유형. 보낸이메일|받은이메일|임시저장이메일
	 * @return
	 */
	@GetMapping("/detail")
	public String emailDetail(int emailNo, Model model, String listType) {

		Email email = emailService.selectOneEmail(emailNo);
		Map<String, String> fileMap = emailService.selectFile(email.getFileNo());

		model.addAttribute("email", email);
		model.addAttribute("listType", listType);
		model.addAttribute("fileMap", fileMap);

		log.info("fileMap={}", fileMap);

		return "/email/emailDetail";
	}

	@RequestMapping("/draftDetail")
	public String draftEmailDetail(int emailNo, Model model) {

		Email email = emailService.selectOneDraftEmail(emailNo);
		Map<String, String> fileMap = emailService.selectFile(email.getFileNo());

		email.setEmailContent(StringEscapeUtils.escapeJavaScript(email.getEmailContent()));
		model.addAttribute("email", email);
		model.addAttribute("fileMap", fileMap);

		return "/email/emailDraft";
	}

	@PostMapping("/draftUpdate")
	public String draftEmailUpdate(Email email) {

		try {
			log.info("email={}", email);
			int result = emailService.updateDraft(email);
			log.info("result={}", result);

			return "redirect:/email/drafts?id=" + email.getId();

		} catch (Exception e) {
			throw e;
		}

	}
	
	@PostMapping("/draft/send")
	public String draftEmailSend(Email email, RedirectAttributes redirectAttr) throws MessagingException {

		try {
			log.info("{}",email);
			emailService.deleteDraft(email.getEmailNo());
			return sendEmail(email, redirectAttr);

		} catch(MessagingException e){
			throw e;
		}

	}

	@GetMapping("/getRecipientList")
	public ResponseEntity<List<String>> getRecipientList(String searchKeyword, String workspaceId) {

		Map<String, String> param = new HashMap<>();

		param.put("searchKeyword", searchKeyword);
		param.put("workspaceId", workspaceId);

		List<String> recipientList = emailService.selectRecipientList(param);

		return ResponseEntity.ok()
				             .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE)
				             .body(recipientList);
	}

	@PostMapping("/saveFile")
	public ResponseEntity<Integer> saveFile(@RequestParam(value = "uploadFile") MultipartFile[] uploadFile)
			throws IOException {
		try {

			String saveDirectory = servletContext.getRealPath(directory);
			Map<String, String> fileMap = FileUtils.getFileMap(uploadFile, saveDirectory);

			emailService.insertFile(fileMap);

			String strNo = String.valueOf(fileMap.get("no"));
			int no = Integer.parseInt(strNo);

			return ResponseEntity.ok()
					             .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE)
					             .body(no);
		
		} catch (IOException e) {
			throw e;
		}
	}
	

	@PostMapping("/send")
	public String sendEmail(Email email, RedirectAttributes redirectAttr) throws MessagingException  {
		try {

			if(email.getExternalRecipient() != null && !email.getExternalRecipient().equals("")) {
				
				String attachDirectory = servletContext.getRealPath(directory);
				String ckDirectory = servletContext.getRealPath("/resources/upload/editorEmailFile");
				
				log.info("email ={}",email);
				Map<String, String> fileMap = emailService.selectFile(email.getFileNo());
				Map<String, File> attachFiles = FileUtils.getAttachFiles(fileMap,  attachDirectory);
				Map<String, File> ckFiles = FileUtils.getAttachFiles(EmailUtils.getFileNames(email.getEmailContent()), ckDirectory);
			
				sendMail(email, ckFiles, attachFiles);
			}
			
			
			int result = emailService.insertEmail(email);

			log.info("email={}", email);
			log.info("InsertResult = {}", result);

			return "redirect:/email/list";

		} catch (IllegalStateException e) {
			throw e;
		
		} catch(MessagingException e){
			throw e;
			
		}catch (NoMemberException e) {
			redirectAttr.addFlashAttribute("msg", "수신자[" + e.getMessage() + "]가 존재하지 않습니다. 이메일을 임시보관합니다.");
			saveDraft(email);
			return "redirect:/email/drafts?id=" + email.getId();
		}
	}

	@GetMapping("/download")
	public ResponseEntity<Resource> fileDownload(String fileReName, String fileOriName)
			throws UnsupportedEncodingException {

		try {

			String saveDirectory = servletContext.getRealPath(directory);
			File downloadFile = new File(saveDirectory, fileReName);
			Resource resource = resourceLoader.getResource("file:" + downloadFile);

			String encodingOriName = "attachment;fileName=\"" + URLEncoder.encode(fileOriName, "UTF-8") + "\"";

			return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
					.header(HttpHeaders.CONTENT_DISPOSITION, encodingOriName).body(resource);

		} catch (UnsupportedEncodingException e) {
			throw e;

		} catch (NullPointerException | IllegalArgumentException e) {
			throw e;
		}
	}

	@RequestMapping("/ckupload")
	@ResponseBody
	public String imageUpload(HttpServletRequest request, HttpServletResponse response,
			MultipartHttpServletRequest multiFile, @RequestParam MultipartFile upload) throws Exception {

		MultipartFile[] m = { upload };

		String saveDirectory = servletContext.getRealPath("/resources/upload/editorEmailFile");
		Map<String, String> fileMap = FileUtils.getFileMap(m, saveDirectory);
		String urlDirectory = request.getContextPath() + "/resources/upload/editorEmailFile/" + fileMap.get("reNamed1");
		JsonObject json = new JsonObject();

		response.setContentType("text/html");

		json.addProperty("uploaded", 1);
		json.addProperty("fileName", fileMap.get("reNamed1"));
		json.addProperty("url", urlDirectory);

		log.info("{}", json);

		new Gson().toJson(json, response.getWriter());

		return null;
	}

	@PostMapping("/draftSave")
	public String saveDraft(Email email) {

		try {

			int result = emailService.insertDraftEmail(email);

			return "redirect:/email/drafts?id=" + email.getId();

		} catch (Exception e) {
			throw e;
		}
	}
	
	/**
	 * 
	 * @param uploadFile : 새로 업로드 된 파일
	 * @param uploadFileReName : 기존의 업로드된 파일 rename값
	 * @param uploadFileOriName : 기존의 업로드된 파일 originalName값
	 * @param fileNo : 기존에 DB에 저장된 파일넘버값. DB에 저장하지 않았다면 0이다.
	 * @return
	 * @throws Exception
	 */
	@PostMapping("/draftFile/update")
	public ResponseEntity<Integer> draftFileUpdate(MultipartFile[] uploadFile, String[] uploadFileReName,
			String[] uploadFileOriName, int fileNo) throws Exception {

		try {
			
			String saveDirectory = servletContext.getRealPath(directory);
			
			//업로드된 파일 & 기존파일이 없다면 원래의 파일을 삭제함.
			if (uploadFile.length == 0 && uploadFileReName == null) {

				emailService.deleteFile(fileNo);

				fileNo = 0;

			} else {
				
				//업로드된 파일이 있다면 새 파일을 저장함.
				Map<String, String> fileMap = FileUtils.getFileMap(uploadFile, saveDirectory);
				
				//새로 저장된 파일의 이름값을 DB에 저장함.
				if (fileNo == 0) {

					emailService.insertFile(fileMap);

					String strNo = String.valueOf(fileMap.get("no"));
					fileNo = Integer.parseInt(strNo);

				} else {
					
					//업로드된 파일 & 기존파일이 있다면 update함.
					int length = uploadFile.length + 1;

					for (int i = 0; i < uploadFileReName.length; i++) {

						fileMap.put(("originalName" + length), uploadFileOriName[i]);
						fileMap.put(("reNamed" + length), uploadFileReName[i]);

						length++;
					}
					
					Map<String, Object> param = new HashMap<String, Object>();

					param.put("fileNo", fileNo);
					param.put("fileMap", fileMap);

					int result = emailService.updateDraftFile(param);
					
					log.info("update result = {}", result);
				}
			}
			
			//파일 삭제
			List<Map<String, String>> renameFileMapList = emailService.selectFileList();
			List<String> renameList = new ArrayList<>();

			for (Map<String, String> map : renameFileMapList) {

				Set<String> keySet = map.keySet();

				for (String key : keySet) {
					renameList.add(map.get(key));
				}
			}

			FileUtils.cleaningFiles(renameList, saveDirectory);

			return ResponseEntity.ok()
					             .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE)
					             .body(fileNo);

		} catch (IOException e) {
			throw e;
		}
	}
	
	//백신프로그램때문에 오류날수도있음.
	public void sendMail(Email email, Map<String, File> ckFiles, Map<String, File> attachFiles) throws MessagingException { 
		
		MimeMessage message = mailSender.createMimeMessage();
	
			try {
				MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
				String[] recipientArr = email.getExternalRecipient().split(", ");

				helper.setFrom(email.getId() + "[Floworks] <floworks@gmail.com>"); 
				helper.setTo(recipientArr);
				helper.setSubject(email.getSubject());
								
				if(attachFiles != null) {
					
					Set<String> attachKeySet = attachFiles.keySet();
					
					for(String key : attachKeySet) {
						helper.addAttachment(key, attachFiles.get(key));
					}					
				}

				if(ckFiles != null) {
	
					Set<String> ckKeySet = ckFiles.keySet();
					List<String> cidList = new ArrayList<>();
					
					for(String key : ckKeySet) {
						cidList.add(key);
					}
					
					String content = StringEscapeUtils.unescapeJava(EmailUtils.addCidToTag(email.getEmailContent(), cidList)
																		.replace("\"", "'"));
					helper.setText(content, true);
					
					//setText에 cid값을 추가한 후 addInline을 해야한다.
					for(String key : ckKeySet) {
						helper.addInline(key, ckFiles.get(key));
					}
					
				}else {
					helper.setText(email.getEmailContent(), true);
				}
				
				mailSender.send(message); 

			} catch (MessagingException e) {
				throw e;
			}
		}
}

