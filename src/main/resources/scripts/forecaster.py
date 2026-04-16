#!/usr/bin/env python3
"""
FORECASTER — OLA Energy · Number_of_deliveries
Modèle : Auto-ARIMA (sélection automatique des meilleurs paramètres)
         + Cross-validation rigoureuse
Usage  : python forecaster.py '<json>'
"""

import sys, json, warnings
import numpy as np
import pandas as pd

warnings.filterwarnings("ignore")


class NumpyEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, np.integer):  return int(obj)
        if isinstance(obj, np.floating): return float(obj)
        if isinstance(obj, np.ndarray):  return obj.tolist()
        return super().default(obj)


# ═══════════════════════════════════════════════════════
# AUTO ARIMA — trouve les meilleurs paramètres
# ═══════════════════════════════════════════════════════
def find_best_order(series):
    """
    Utilise pmdarima pour trouver automatiquement
    les meilleurs paramètres SARIMA via AIC.
    """
    try:
        import pmdarima as pm
        print("[INFO] Auto-ARIMA : recherche des meilleurs paramètres...", file=sys.stderr)

        model = pm.auto_arima(
            series,
            start_p=0, max_p=2,
            start_q=0, max_q=2,
            d=None,           # détection automatique différenciation
            start_P=0, max_P=2,
            start_Q=0, max_Q=2,
            D=1,              # différenciation saisonnière
            m=12,             # saisonnalité annuelle
            seasonal=True,
            stepwise=True,    # recherche intelligente (plus rapide)
            information_criterion="aic",
            error_action="ignore",
            suppress_warnings=True,
        )

        order          = model.order
        seasonal_order = model.seasonal_order
        aic            = round(float(model.aic()), 2)

        print(f"[INFO] Meilleur modèle : SARIMA{order}x{seasonal_order} AIC={aic}", file=sys.stderr)
        return order, seasonal_order, aic

    except ImportError:
        # pmdarima pas installé → utiliser paramètres par défaut
        print("[WARN] pmdarima non installé → paramètres par défaut (1,1,1)(1,1,1,12)", file=sys.stderr)
        return (1,1,1), (1,1,1,12), None


# ═══════════════════════════════════════════════════════
# SARIMA — entraînement avec les paramètres trouvés
# ═══════════════════════════════════════════════════════
def sarima_forecast(series, order, seasonal_order, periods):
    from statsmodels.tsa.statespace.sarimax import SARIMAX

    model  = SARIMAX(
        series,
        order          = order,
        seasonal_order = seasonal_order,
        enforce_stationarity  = False,
        enforce_invertibility = False,
    )
    fitted = model.fit(disp=False)
    fc     = fitted.get_forecast(steps=periods)
    mean   = fc.predicted_mean
    ci     = fc.conf_int(alpha=0.05)

    preds = []
    for i in range(periods):
        preds.append({
            "month":     mean.index[i].strftime("%Y-%m"),
            "predicted": round(max(0, float(mean.iloc[i])), 0),
            "lower":     round(max(0, float(ci.iloc[i, 0])), 0),
            "upper":     round(max(0, float(ci.iloc[i, 1])), 0),
        })

    return preds, round(float(fitted.aic), 2)


# ═══════════════════════════════════════════════════════
# CROSS-VALIDATION rigoureuse
# Walk-forward : teste sur les 3 derniers mois
# ═══════════════════════════════════════════════════════
def cross_validate(series, order, seasonal_order):
    try:
        from statsmodels.tsa.statespace.sarimax import SARIMAX

        if len(series) < 15:
            return {"mae": None, "mape": None}

        n_test = 3
        train  = series.iloc[:-n_test]
        test   = series.iloc[-n_test:]

        model  = SARIMAX(
            train,
            order          = order,
            seasonal_order = seasonal_order,
            enforce_stationarity  = False,
            enforce_invertibility = False,
        )
        fitted = model.fit(disp=False)
        fc     = fitted.get_forecast(steps=n_test)
        preds  = np.maximum(fc.predicted_mean.values, 0)
        actual = test.values

        errors = np.abs(preds - actual)
        mae    = float(np.mean(errors))
        mape   = float(np.mean(errors / actual) * 100)

        # Erreur par mois
        months = [test.index[i].strftime("%Y-%m") for i in range(n_test)]
        detail = [
            {
                "month":     months[i],
                "predicted": round(float(preds[i]), 0),
                "actual":    round(float(actual[i]), 0),
                "error":     round(float(errors[i]), 0),
                "error_pct": round(float(errors[i] / actual[i] * 100), 2),
            }
            for i in range(n_test)
        ]

        return {
            "mae":    round(mae, 2),
            "mape":   round(mape, 2),
            "detail": detail,
        }

    except Exception as e:
        return {"mae": None, "mape": None, "error": str(e)}


# ═══════════════════════════════════════════════════════
# PRÉDICTION PRINCIPALE
# ═══════════════════════════════════════════════════════
def forecast(data):
    affiliate = data.get("affiliate", "UNKNOWN")
    kpi       = data.get("kpi", "Number_of_deliveries")
    history   = data.get("history", [])
    periods   = int(data.get("periods", 1))

    if len(history) < 12:
        return {"error": f"Minimum 12 mois requis. Reçu : {len(history)}"}
    if periods > 3:
        return {"error": "Maximum 3 mois recommandé"}

    # ── DataFrame ──────────────────────────────────────────────────────
    df = pd.DataFrame({
        "ds": pd.to_datetime([h["ds"] for h in history]),
        "y":  [float(h["y"]) for h in history]
    }).sort_values("ds").reset_index(drop=True)

    series = df.set_index("ds")["y"]
    series.index = pd.DatetimeIndex(series.index).to_period("M").to_timestamp()

    print(f"[INFO] {len(series)} mois | moy={series.mean():.0f} min={series.min():.0f} max={series.max():.0f}", file=sys.stderr)

    # ── Trouver les meilleurs paramètres ──────────────────────────────
    order, seasonal_order, auto_aic = find_best_order(series)

    # ── Cross-validation ───────────────────────────────────────────────
    print("[INFO] Cross-validation...", file=sys.stderr)
    cv = cross_validate(series, order, seasonal_order)
    if cv.get("mape") is not None:
        print(f"[INFO] CV MAPE={cv['mape']:.2f}% MAE={cv['mae']:.0f}", file=sys.stderr)

    # ── Entraînement final ─────────────────────────────────────────────
    print("[INFO] Entraînement modèle final...", file=sys.stderr)
    predictions, aic = sarima_forecast(series, order, seasonal_order, periods)
    print(f"[INFO] AIC={aic} | Prédit={[p['predicted'] for p in predictions]}", file=sys.stderr)

    # ── Tendance ───────────────────────────────────────────────────────
    recent = float(df["y"].tail(3).mean())
    older  = float(df["y"].head(3).mean())
    tpct   = ((recent - older) / older * 100) if older != 0 else 0
    tlabel = "hausse" if tpct > 5 else ("baisse" if tpct < -5 else "stable")

    # ── Confiance ──────────────────────────────────────────────────────
    p0         = predictions[0]
    interval   = p0["upper"] - p0["lower"]
    confidence = max(0, min(100, round(100 - (interval / p0["predicted"] * 100), 1))) \
        if p0["predicted"] > 0 else 0

    # ── Qualité globale ────────────────────────────────────────────────
    mape = cv.get("mape")
    if   mape and mape < 5:  quality = "Excellent"
    elif mape and mape < 10: quality = "Bon"
    elif mape and mape < 20: quality = "Acceptable"
    else:                    quality = "Faible"

    return {
        "affiliate":   affiliate,
        "kpi":         kpi,
        "month":       p0["month"],
        "predicted":   p0["predicted"],
        "lower":       p0["lower"],
        "upper":       p0["upper"],
        "predictions": predictions,
        "trend":       tlabel,
        "trend_pct":   round(tpct, 2),
        "confidence":  confidence,
        "quality":     quality,
        "cv_mape":     cv.get("mape"),
        "cv_mae":      cv.get("mae"),
        "cv_detail":   cv.get("detail"),
        "history": [
            {"month": r["ds"].strftime("%Y-%m"), "value": round(float(r["y"]), 0)}
            for _, r in df.iterrows()
        ],
        "model_info": {
            "algorithm":      f"SARIMA{order}x{seasonal_order}",
            "order":          str(order),
            "seasonal_order": str(seasonal_order),
            "aic":            aic,
            "selection":      "Auto-ARIMA (AIC optimal)" if auto_aic else "Paramètres par défaut",
            "n_train_months": len(series),
            "periods":        periods,
        }
    }


# ═══════════════════════════════════════════════════════
# POINT D'ENTRÉE
# ═══════════════════════════════════════════════════════
if __name__ == "__main__":
    try:
        if len(sys.argv) < 2:
            print(json.dumps({"error": "Usage: python forecaster.py '<json>' ou python forecaster.py --file input.json"}))
            sys.exit(1)

        # Lire depuis fichier (Spring Boot) ou argument direct (terminal)
        if sys.argv[1] == "--file":
            with open(sys.argv[2], "r", encoding="utf-8") as f:
                input_data = json.load(f)
        else:
            input_data = json.loads(sys.argv[1])

        result = forecast(input_data)
        output = json.dumps(result, ensure_ascii=True, cls=NumpyEncoder)
        sys.stdout.buffer.write(output.encode('utf-8'))
        sys.stdout.buffer.write(b'\n')

    except json.JSONDecodeError as e:
        print(json.dumps({"error": f"JSON invalide : {str(e)}"}))
        sys.exit(1)
    except Exception as e:
        import traceback
        print(json.dumps({"error": str(e), "trace": traceback.format_exc()}))
        sys.exit(1)