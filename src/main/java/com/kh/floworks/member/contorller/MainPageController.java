package com.kh.floworks.member.contorller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 메인페이지,
 * 로그인,
 * 로그아웃,
 * 회원 가입,
 * 워크스페이스 홈페이지,
 * 
 * 컨트롤러
 * 
 */
@Controller
public class MainPageController {
	
	@GetMapping("/")
	public String main(Model model) {
		return "mainPage";
	}
	
	@GetMapping("/home")
	public String home() {
		return "/home";
	}
	
	@GetMapping("/mainPage")
	public String indexPage() {
		return "/member/mainPage";
	}

	@GetMapping("/login")
	public String login() {	
		return "/member/login";
	}
	
	@PostMapping("/login") //로그인 처리 X  | 로그인 실패 후 POST 요청 처리 O 
	public String loginFail() {		
		return "/member/login";
	}
	
	@GetMapping("/register")
	public String register() {
		return "/member/register"; 
	}
	
	@GetMapping("/createWorkspace")
	public String createWorkspace() {
		return "/member/createWorkspace"; 
	}
	
	
}