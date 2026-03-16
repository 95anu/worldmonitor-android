from pydantic_settings import BaseSettings, SettingsConfigDict
from pathlib import Path


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    SERVER_HOST: str = "0.0.0.0"
    SERVER_PORT: int = 8000
    DB_PATH: str = "./worldmonitor.db"
    NASA_FIRMS_API_KEY: str = ""
    REFRESH_INTERVAL_MINUTES: int = 15
    MAX_ARTICLES_AGE_DAYS: int = 3
    LOG_LEVEL: str = "INFO"

    @property
    def db_url(self) -> str:
        return f"sqlite:///{self.DB_PATH}"

    @property
    def firms_enabled(self) -> bool:
        return bool(self.NASA_FIRMS_API_KEY.strip())


settings = Settings()
