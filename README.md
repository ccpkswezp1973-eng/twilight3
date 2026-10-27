# Sword Swing Motion (Fabric, 클라이언트 전용)

검을 들고 공격할 때마다 내려찍기 → 쓸어베기 → 올려베기 3연속 콤보로 1인칭 휘두르기 모션을 바꾸는 모드입니다.

## jar 파일 만들기 (mods 폴더에 넣는 파일)

### 방법 A. GitHub에서 자동 빌드 (내 컴퓨터에 설치 불필요)
1. GitHub에서 새 저장소를 만들고 이 폴더의 파일을 전부 업로드 (`.github` 폴더 포함)
2. 저장소의 Actions 탭 → build 실행이 끝나길 기다림
3. 완료된 실행 화면 아래쪽 Artifacts의 `sword-swing-mod`를 받아 압축을 풀면 jar가 나옴

### 방법 B. 내 컴퓨터에서 빌드
1. JDK 25와 Gradle을 설치
2. 이 폴더에서 `gradle build`
3. `build/libs/sword-swing-mod-1.0.0.jar` 사용
   (게임을 띄워 테스트만 하려면 `gradle runClient`)

## 설치
- Fabric Loader 0.19.5 이상이 설치된 마인크래프트 26.1.2의 `mods` 폴더에 jar를 넣으면 됩니다.
- Fabric API는 필요 없습니다.

## 모션 조절
`ItemInHandRendererMixin.java` 위쪽의 WINDUP / SLASH 표({ yaw, roll, pitch })를 바꾸세요.
방향이 반대로 느껴지면 해당 숫자의 부호를 뒤집으면 됩니다.

## 묵직한 느낌
스윙 속도(전체 시간)는 바닐라 그대로이고, 시간 배분만 바꿔서 묵직하게 만듭니다.
천천히 힘 모으기 → 순간 가속 베기 → 살짝 지나쳤다 돌아옴 → 잠깐 정지 → 천천히 복귀.
`POWER`, `OVERSHOOT`, `IMPACT_*` 상수로 정도를 조절하세요.

## 문제 해결
- 빌드 에러로 클래스를 못 찾으면: 26.x에서 패키지 위치가 바뀐 것일 수 있으니 IDE의 자동 import로 수정
- "applyItemArmAttackTransform"을 못 찾는다는 Mixin 에러: 해당 버전의 ItemInHandRenderer 소스에서 메서드 이름 확인
