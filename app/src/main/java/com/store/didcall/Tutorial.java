package com.store.didcall;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

/**
 * 첫 설치 튜토리얼: 기기 역할에 맞는 설정을 단계별로 안내.
 * 각 단계는 설명 + [다음] 버튼이고, 실행할 수 있는 단계는 중앙 버튼으로 실제 설정 대화상자를 열어 줌.
 * [완료] 또는 언제든 [건너뛰기]를 누르면 done 콜백으로 해당 화면(호출기/TV)으로 넘어감.
 */
final class Tutorial {

    private Tutorial() {}

    /** 단계 하나: 제목, 설명, (선택) 지금 설정 버튼 문구·동작 */
    static class Step {
        final String title, body;
        final String actionLabel;      // null 이면 버튼 없음
        final Runnable action;         // actionLabel 이 있을 때 실행
        Step(String title, String body, String actionLabel, Runnable action) {
            this.title = title; this.body = body; this.actionLabel = actionLabel; this.action = action;
        }
    }

    /** 호출기(호스트) 튜토리얼 — CallerActivity 의 설정 대화상자를 바로 열어 줌 */
    static void showCaller(CallerActivity a, Runnable done, Runnable skip) {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(
                "📱 호출기로 쓸게요",
                "이 폰이 호출 서버(호스트)입니다.\n\n"
                + "① 와이파이 연결 확인 — TV·PC와 같은 와이파이(공유기)에 연결해야 합니다\n"
                + "② 화면 상단에 표시되는 '이 폰 주소'를 잘 적어 두세요 (TV 연결 시 필요)",
                null, null));
        steps.add(new Step(
                "🖼 매장 사진·동영상 넣기",
                "TV에 표시될 매장 사진이나 동영상을 넣습니다.\n\n"
                + "여러 장을 고르면 자동으로 넘어가며 표시됩니다.",
                "🖼 이미지 추가", () -> a.runOnUiThread(a::pickImageForTutorial)));
        steps.add(new Step(
                "⌨ 블루투스 키패드",
                "블루투스 숫자 키패드(텐키)가 있으면 연결해 보세요.\n\n"
                + "• 연결하면 화면의 버튼 없이 키패드로 번호를 입력하고 [호출]할 수 있습니다\n"
                + "• 기본 배치는 텐키형입니다 (Enter=호출, +=다음 번호, *=재호출)\n"
                + "• 키패드가 없으면 화면 버튼으로 똑같이 쓸 수 있습니다",
                "⌨ 키 설정 보기", () -> a.runOnUiThread(a::showKeySettings)));
        steps.add(new Step(
                "🔔 소리 설정 (알림음·목소리)",
                "TV에서 나올 알림음과 음성을 고릅니다.\n\n"
                + "기본값: 딩동 + 'OO번 고객님, 주문하신 메뉴가 준비되었습니다'",
                "🔔 알림음 선택", () -> a.runOnUiThread(a::showChimesForTutorial)));
        steps.add(new Step(
                "🧾 키오스크 주문 연동 (매장에 키오스크가 있을 때)",
                "키오스크 주문서를 받아 '조리중' 목록에 자동으로 넣습니다.\n\n"
                + "분기 프로그램의 '네트워크 프린터' 주소를 이 폰의 주소(:9100)로 넣으면\n"
                + "주문이 들어올 때마다 조리중 목록에 번호가 쌓입니다.\n\n"
                + "키오스크가 없으면 건너뛰어도 됩니다.",
                "🧾 키오스크 설정", () -> a.runOnUiThread(a::showKioskForTutorial)));
        steps.add(new Step(
                "📺 디스플레이 기기 선택",
                "호출 번호를 보여줄 화면을 정합니다.\n\n"
                + "📺 안드로이드 TV/스틱 — TV 기기에 이 앱을 설치하고 'TV 화면' 선택 (자동으로 이 폰을 찾음)\n"
                + "💻 윈도우 PC — 브라우저로 이 폰 주소/display 접속\n"
                + "📱 다른 태블릿/폰 — 브라우저로 이 폰 주소/display 접속",
                "📺 연결 방법 보기", () -> a.runOnUiThread(a::showConnectInfoForTutorial)));
        steps.add(new Step(
                "🎨 TV 화면 배치 (선택)",
                "TV 화면의 구역(이미지·최근 호출·조리중·로고)을 자유롭게 옮기고 크기를 바꿀 수 있습니다.\n\n"
                + "⚙ 메뉴 → '이미지·동영상 관리 · 기타 설정 (브라우저)' → 화면 배치\n\n"
                + "구역을 끌어서 옮기고, 모서리로 크기 조절, 체크로 보이기/숨기기.\n"
                + "기본 배치로도 바로 쓸 수 있으니 나중에 해도 됩니다.",
                null, null));
        steps.add(new Step(
                "✅ 마지막으로",
                "TV가 이 폰을 찾으면 화면 상단에 'TV/PC 화면 N대 연결됨'이 초록색으로 표시됩니다.\n\n"
                + "번호를 입력하고 [호출]을 눌러 보세요!",
                null, null));
        run(a, "호출기 설정", steps, done, skip);
    }

    /** TV(수신기) 튜토리얼 */
    static void showTv(Activity a, Runnable done, Runnable skip) {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(
                "📺 TV 화면으로 쓸게요",
                "이 기기는 호출 번호를 화면에 표시합니다.\n\n"
                + "호출기 폰의 DID 앱이 켜져 있고, 같은 와이파이에 연결되어 있어야 합니다.\n"
                + "지금 자동으로 호출기를 찾는 중입니다.",
                null, null));
        steps.add(new Step(
                "⚡ 부팅 시 자동 실행",
                "TV를 켤 때마다 이 앱이 자동으로 열리려면 권한이 필요합니다.\n\n"
                + "설정 → '다른 앱 위에 표시'를 허용하면 부팅 후 자동으로 실행됩니다.",
                null, null));
        steps.add(new Step(
                "🔄 화면 방향",
                "TV를 세로로 돌려 세웠다면 화면도 돌려야 합니다.\n\n"
                + "뒤로 버튼 → '화면 방향 (세로 거치)'에서 선택\n\n"
                + "가로로 쓰면 그대로 두면 됩니다.",
                null, null));
        steps.add(new Step(
                "🖼 이미지 위치 (선택)",
                "이미지·동영상이 표시되는 위치와 크기는 호출기 폰에서 정합니다.\n\n"
                + "호출기 폰 → ⚙ 메뉴 → '이미지·동영상 관리 · 기타 설정 (브라우저)' → 화면 배치\n\n"
                + "TV 화면의 구역(이미지·최근 호출·조리중·로고)을 끌어서 옮기고 크기를 조절할 수 있습니다.",
                null, null));
        steps.add(new Step(
                "✅ 완료",
                "호출기 폰이 켜져 있으면 곧 호출 번호가 표시됩니다.\n\n"
                + "'호출기를 찾지 못했습니다'가 계속 보이면:\n"
                + "① 호출기 폰의 DID 앱이 켜져 있는지\n"
                + "② 같은 와이파이인지\n"
                + "③ [주소 직접 입력]으로 호출기 앱 상단의 주소 입력",
                null, null));
        run(a, "TV 화면 설정", steps, done, skip);
    }

    private static void run(Activity a, String title, List<Step> steps, Runnable done, Runnable skip) {
        final int[] idx = {0};
        AlertDialog[] dlg = new AlertDialog[1];
        dlg[0] = new AlertDialog.Builder(a, R.style.DidDialog)
                .setTitle(title)
                .setMessage("")
                .setPositiveButton("다음", null)  // 아래에서 덮어씀
                .setNeutralButton("", null)       // 단계별 '지금 설정' 버튼 (아래에서 덮어씀)
                .setNegativeButton("건너뛰기", (d, w) -> { d.dismiss(); skip.run(); })
                .setCancelable(false)
                .create();
        dlg[0].setOnShowListener(d -> {
            Button next = dlg[0].getButton(AlertDialog.BUTTON_POSITIVE);
            Button action = dlg[0].getButton(AlertDialog.BUTTON_NEUTRAL);
            Runnable render = () -> {
                Step s = steps.get(idx[0]);
                dlg[0].setTitle(title + "  " + (idx[0] + 1) + "/" + steps.size());
                dlg[0].setMessage(s.title + "\n\n" + s.body);
                next.setText(idx[0] == steps.size() - 1 ? "완료" : "다음");
                if (s.actionLabel != null && s.action != null) {
                    action.setVisibility(Button.VISIBLE);
                    action.setText(s.actionLabel);
                } else {
                    action.setVisibility(Button.GONE);
                }
            };
            next.setOnClickListener(v -> {
                if (idx[0] < steps.size() - 1) { idx[0]++; render.run(); }
                else { dlg[0].dismiss(); done.run(); }
            });
            action.setOnClickListener(v -> steps.get(idx[0]).action.run());
            render.run();
        });
        dlg[0].show();
    }
}
