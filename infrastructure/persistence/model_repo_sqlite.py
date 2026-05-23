from typing import List, Optional
from domain.entities.model_config import ModelConfig
from domain.repositories.model_repo import ModelRepository
from infrastructure.persistence.database import get_connection


class SqliteModelRepository(ModelRepository):
    def create(self, config: ModelConfig) -> ModelConfig:
        conn = get_connection()
        if config.is_default:
            conn.execute("UPDATE model_configs SET is_default=FALSE WHERE user_id=%s", (config.user_id,))
        cursor = conn.execute(
            """INSERT INTO model_configs
            (user_id, name, model_type, model, api_key, api_endpoint, temperature, max_tokens, is_default, enabled)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            RETURNING id""",
            (config.user_id, config.name, config.model_type, config.model,
             config.api_key, config.api_endpoint, config.temperature, config.max_tokens,
             config.is_default, config.enabled),
        )
        row = cursor.fetchone()
        config.id = row[0]
        conn.commit()
        conn.close()
        return config

    def get_by_device(self, user_id: str) -> List[ModelConfig]:
        conn = get_connection()
        cursor = conn.execute(
            "SELECT * FROM model_configs WHERE user_id=%s ORDER BY is_default DESC, id ASC",
            (user_id,),
        )
        rows = cursor.fetchall()
        conn.close()
        return [self._row_to_entity(r) for r in rows]

    def get_default(self, user_id: str) -> Optional[ModelConfig]:
        conn = get_connection()
        cursor = conn.execute(
            "SELECT * FROM model_configs WHERE user_id=%s AND is_default=TRUE AND enabled=TRUE LIMIT 1",
            (user_id,),
        )
        row = cursor.fetchone()
        conn.close()
        if not row:
            return None
        return self._row_to_entity(row)

    def get_by_id(self, model_id: int, user_id: str) -> Optional[ModelConfig]:
        conn = get_connection()
        cursor = conn.execute(
            "SELECT * FROM model_configs WHERE id=%s AND user_id=%s",
            (model_id, user_id)
        )
        row = cursor.fetchone()
        conn.close()
        if not row:
            return None
        return self._row_to_entity(row)

    def update(self, config: ModelConfig) -> ModelConfig:
        conn = get_connection()
        if config.is_default:
            conn.execute("UPDATE model_configs SET is_default=FALSE WHERE user_id=%s", (config.user_id,))
        conn.execute(
            """UPDATE model_configs SET name=%s, model_type=%s, model=%s, api_key=%s, api_endpoint=%s,
            temperature=%s, max_tokens=%s, is_default=%s, enabled=%s, updated_at=CURRENT_TIMESTAMP
            WHERE id=%s AND user_id=%s""",
            (config.name, config.model_type, config.model, config.api_key, config.api_endpoint,
             config.temperature, config.max_tokens, config.is_default, config.enabled,
             config.id, config.user_id),
        )
        conn.commit()
        conn.close()
        return config

    def delete(self, model_id: int, user_id: str) -> bool:
        conn = get_connection()
        cursor = conn.execute(
            "DELETE FROM model_configs WHERE id=%s AND user_id=%s",
            (model_id, user_id)
        )
        conn.commit()
        conn.close()
        return cursor.rowcount > 0

    def upsert(self, config: ModelConfig) -> ModelConfig:
        """Insert or update a model config using PostgreSQL upsert."""
        conn = get_connection()
        try:
            if config.is_default:
                conn.execute("UPDATE model_configs SET is_default=FALSE WHERE user_id=%s", (config.user_id,))

            if config.id:
                cursor = conn.execute(
                    "SELECT id FROM model_configs WHERE id=%s AND user_id=%s",
                    (config.id, config.user_id)
                )
                existing = cursor.fetchone()

                if existing:
                    conn.execute(
                        """UPDATE model_configs SET name=%s, model_type=%s, model=%s, api_key=%s, api_endpoint=%s,
                        temperature=%s, max_tokens=%s, is_default=%s, enabled=%s, updated_at=CURRENT_TIMESTAMP
                        WHERE id=%s AND user_id=%s""",
                        (config.name, config.model_type, config.model, config.api_key, config.api_endpoint,
                         config.temperature, config.max_tokens, config.is_default, config.enabled,
                         config.id, config.user_id),
                    )
                else:
                    cursor = conn.execute(
                        """INSERT INTO model_configs
                        (user_id, name, model_type, model, api_key, api_endpoint, temperature, max_tokens, is_default, enabled)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                        RETURNING id""",
                        (config.user_id, config.name, config.model_type, config.model,
                         config.api_key, config.api_endpoint, config.temperature, config.max_tokens,
                         config.is_default, config.enabled),
                    )
                    row = cursor.fetchone()
                    config.id = row[0]
            else:
                cursor = conn.execute(
                    """INSERT INTO model_configs
                    (user_id, name, model_type, model, api_key, api_endpoint, temperature, max_tokens, is_default, enabled)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    RETURNING id""",
                    (config.user_id, config.name, config.model_type, config.model,
                     config.api_key, config.api_endpoint, config.temperature, config.max_tokens,
                     config.is_default, config.enabled),
                )
                row = cursor.fetchone()
                config.id = row[0]

            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()
        return config

    @staticmethod
    def _row_to_entity(r) -> ModelConfig:
        return ModelConfig(
            id=r["id"], user_id=r["user_id"], name=r["name"],
            model_type=r["model_type"], model=r["model"], api_key=r["api_key"],
            api_endpoint=r["api_endpoint"], temperature=r["temperature"],
            max_tokens=r["max_tokens"], is_default=bool(r["is_default"]),
            enabled=bool(r["enabled"]),
        )