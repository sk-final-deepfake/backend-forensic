# 🧬 ForenShield 프로젝트 가이드 (GEMINI.md)

이 파일은 AI 아시스턴트가 이 프로젝트에서 작업할 때 준수해야 할 핵심 지침을 담고 있습니다.

## 📋 핵심 규칙 참조
- **팀 협업 규칙**: [docs/00-rule.md](docs/rule.md)를 최우선으로 준수하십시오.
- **커밋 메시지**: 반드시 `feat:`, `fix:`, `docs:`, `style:`, `refactor:`, `chore:` 말머리를 사용하십시오.
- **브랜치 운영**: `feature/` 브랜치에서 작업하고 `develop`으로 통합하는 흐름을 따르십시오.

## 🏗️ 아키텍처 가이드
- **Backend**: Spring Boot 3.x
- **Database**: PostgreSQL (docs/01-database-schema.md 참조)
- **Messaging**: RabbitMQ (docs/03-rabbitmq-pipeline.md 참조)
- **AI Interface**: docs/04-ai-json-spec.md의 JSON 규격 준수

## 🔐 보안 및 무결성
- S3 원본 데이터는 Object Lock(WORM) 정책에 따라 수정/삭제가 불가함을 인지하고 작업하십시오.
- 모든 행위는 `coc_logs` 테이블에 기록되어야 합니다.
